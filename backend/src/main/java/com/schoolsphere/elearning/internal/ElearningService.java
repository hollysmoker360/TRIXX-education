package com.schoolsphere.elearning.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.learning.api.LearningProgressApi;
import com.schoolsphere.learning.api.LearningProgressApi.ItemInfo;
import com.schoolsphere.learning.api.LearningProgressApi.ItemOutcome;
import com.schoolsphere.learning.api.LearningProgressApi.ItemRef;
import com.schoolsphere.learning.api.LearningProgressApi.ItemType;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.people.api.PeopleApi.StudentRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * E-Learning courses: ordered learning paths over existing resources, assignments and quizzes, with
 * learner enrollment. Progress counts completed required items; outcomes (published grades and quiz
 * scores) are reported separately from completion.
 */
@Service
@TenantScoped
public class ElearningService {

    private final CourseRepository courseRepository;
    private final CourseItemRepository itemRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final PeopleApi peopleApi;
    private final LearningProgressApi progressApi;
    private final AuditLogService auditLogService;

    public ElearningService(CourseRepository courseRepository, CourseItemRepository itemRepository,
                            CourseEnrollmentRepository enrollmentRepository, PeopleApi peopleApi,
                            LearningProgressApi progressApi, AuditLogService auditLogService) {
        this.courseRepository = courseRepository;
        this.itemRepository = itemRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.peopleApi = peopleApi;
        this.progressApi = progressApi;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------------------------------------
    // Courses
    // ---------------------------------------------------------------------------------------------

    /** Staff view: every course with its size and the average progress of enrolled learners. */
    public PageResponse<CourseView> listCourses(String status, int page, int size) {
        Page<Course> result = courseRepository.search(schoolId(), blankToNull(status), pageRequest(page, size));
        List<UUID> ids = result.getContent().stream().map(Course::getId).toList();
        Map<UUID, List<CourseItem>> items = itemsByCourse(ids);
        Map<UUID, List<CourseEnrollment>> enrollments = ids.isEmpty() ? Map.of()
                : enrollmentRepository.findBySchoolIdAndCourseIdInAndDeletedAtIsNull(schoolId(), ids).stream()
                .collect(Collectors.groupingBy(CourseEnrollment::getCourseId));
        return PageResponse.of(result.map(course -> {
            List<CourseItem> courseItems = items.getOrDefault(course.getId(), List.of());
            List<CourseEnrollment> courseEnrollments = enrollments.getOrDefault(course.getId(), List.of());
            int average = courseEnrollments.isEmpty() ? 0 : (int) Math.round(courseEnrollments.stream()
                    .mapToInt(e -> progress(course, courseItems, e).progress()).average().orElse(0));
            return toCourseView(course, courseItems.size(), courseEnrollments.size(), average, false, null);
        }));
    }

    /** Learner view: published courses open to the student's class, with personal progress. */
    public List<CourseView> listCoursesForMe() {
        StudentRef student = requireStudent();
        Map<UUID, CourseEnrollment> enrolled = enrollmentRepository
                .findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId(), student.id()).stream()
                .collect(Collectors.toMap(CourseEnrollment::getCourseId, Function.identity(), (a, b) -> a));
        Map<UUID, Course> courses = new HashMap<>();
        courseRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId(), enrolled.keySet())
                .forEach(c -> courses.put(c.getId(), c));
        Map<UUID, Boolean> membership = new HashMap<>();
        for (Course course : courseRepository.search(schoolId(), "PUBLISHED", PageRequest.of(0, 500)).getContent()) {
            if (course.getClassGroupId() == null || membership.computeIfAbsent(course.getClassGroupId(),
                    cls -> isInClass(student.id(), cls))) {
                courses.putIfAbsent(course.getId(), course);
            }
        }
        Map<UUID, List<CourseItem>> items = itemsByCourse(new ArrayList<>(courses.keySet()));
        return courses.values().stream()
                .filter(c -> !"DRAFT".equals(c.getStatus()))
                .sorted(Comparator.comparing(Course::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(course -> {
                    List<CourseItem> courseItems = items.getOrDefault(course.getId(), List.of());
                    CourseEnrollment enrollment = enrolled.get(course.getId());
                    Progress p = enrollment == null ? Progress.NONE : progress(course, courseItems, enrollment);
                    return toCourseView(course, courseItems.size(), 0, p.progress(), enrollment != null,
                            enrollment == null ? null : enrollment.getCompletedAt());
                })
                .toList();
    }

    public CourseView createCourse(CourseInput input) {
        Course course = new Course();
        course.setSchoolId(schoolId());
        course.setCreatedBy(TenantContext.actorUserId());
        apply(course, input, true);
        Course saved = courseRepository.save(course);
        auditLogService.record("elearning.course.created", "course", saved.getId(), null, toCourseView(saved, 0, 0, 0, false, null));
        return toCourseView(saved, 0, 0, 0, false, null);
    }

    public CourseView updateCourse(UUID id, CourseInput input) {
        Course course = requireCourse(id);
        if ("ARCHIVED".equals(course.getStatus())) {
            throw ApiException.conflict("course_archived", "Archived courses cannot be edited.");
        }
        apply(course, input, false);
        return save(course, "elearning.course.updated");
    }

    public CourseView publishCourse(UUID id) {
        Course course = requireCourse(id);
        if (!"DRAFT".equals(course.getStatus())) {
            throw ApiException.conflict("invalid_course_state", "Only draft courses can be published.");
        }
        if (itemRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(schoolId(), id).isEmpty()) {
            throw ApiException.badRequest("course_empty", "Add at least one learning item before publishing.");
        }
        course.setStatus("PUBLISHED");
        course.setPublishedAt(Instant.now());
        return save(course, "elearning.course.published");
    }

    public CourseView archiveCourse(UUID id) {
        Course course = requireCourse(id);
        course.setStatus("ARCHIVED");
        return save(course, "elearning.course.archived");
    }

    public void deleteCourse(UUID id) {
        Course course = requireCourse(id);
        if (!"DRAFT".equals(course.getStatus())
                || !enrollmentRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNull(schoolId(), id).isEmpty()) {
            throw ApiException.conflict("course_in_use", "Only draft courses without learners can be deleted; archive it instead.");
        }
        course.setDeletedAt(Instant.now());
        course.setUpdatedBy(TenantContext.actorUserId());
        courseRepository.save(course);
        auditLogService.record("elearning.course.deleted", "course", id, null, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    public List<ItemView> listItems(UUID courseId) {
        requireCourse(courseId);
        return itemRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(schoolId(), courseId)
                .stream().map(this::toItemView).toList();
    }

    public ItemView addItem(UUID courseId, String itemType, UUID refId, Boolean required) {
        Course course = requireCourse(courseId);
        if ("ARCHIVED".equals(course.getStatus())) {
            throw ApiException.conflict("course_archived", "Archived courses cannot change.");
        }
        ItemType type = parseType(itemType);
        if (refId == null) {
            throw ApiException.badRequest("ref_required", "Choose the resource, assignment or quiz to add.");
        }
        ItemInfo info = progressApi.findItem(schoolId(), type, refId)
                .orElseThrow(() -> ApiException.notFound("learning_item_not_found", "That learning item does not exist."));
        if (itemRepository.existsBySchoolIdAndCourseIdAndItemTypeAndRefIdAndDeletedAtIsNull(schoolId(), courseId,
                type.name(), refId)) {
            throw ApiException.conflict("item_already_added", "This item is already part of the course.");
        }
        List<CourseItem> existing = itemRepository
                .findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(schoolId(), courseId);
        CourseItem item = new CourseItem();
        item.setSchoolId(schoolId());
        item.setCourseId(courseId);
        item.setItemType(type.name());
        item.setRefId(refId);
        item.setTitle(info.title());
        item.setRequired(required == null || required);
        item.setPosition(existing.stream().mapToInt(CourseItem::getPosition).max().orElse(0) + 1);
        item.setCreatedBy(TenantContext.actorUserId());
        item.setUpdatedBy(TenantContext.actorUserId());
        CourseItem saved = itemRepository.save(item);
        auditLogService.record("elearning.item.added", "course_item", saved.getId(), null, toItemView(saved));
        return toItemView(saved);
    }

    public void removeItem(UUID courseId, UUID itemId) {
        requireCourse(courseId);
        CourseItem item = itemRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), itemId)
                .filter(i -> i.getCourseId().equals(courseId))
                .orElseThrow(() -> ApiException.notFound("course_item_not_found", "Course item not found."));
        item.setDeletedAt(Instant.now());
        item.setUpdatedBy(TenantContext.actorUserId());
        itemRepository.save(item);
        auditLogService.record("elearning.item.removed", "course_item", itemId, toItemView(item), null);
    }

    // ---------------------------------------------------------------------------------------------
    // Enrollment and progress
    // ---------------------------------------------------------------------------------------------

    /** Enrolls the given students, or the course's class when none are given; returns how many were added. */
    public EnrollResult enroll(UUID courseId, List<UUID> studentIds) {
        Course course = requirePublished(courseId);
        List<UUID> requested;
        if (studentIds == null || studentIds.isEmpty()) {
            if (course.getClassGroupId() == null) {
                throw ApiException.badRequest("no_class", "This course has no class; choose the learners to enroll.");
            }
            requested = peopleApi.listStudentsByClass(schoolId(), course.getClassGroupId(), null).stream()
                    .map(StudentRef::id).toList();
        } else {
            Set<UUID> unique = new LinkedHashSet<>(studentIds);
            requested = peopleApi.findStudents(schoolId(), new ArrayList<>(unique)).stream().map(StudentRef::id).toList();
            if (requested.size() != unique.size()) {
                throw ApiException.badRequest("unknown_students", "Some learners are not students of this school.");
            }
        }
        Set<UUID> already = enrollmentRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNull(schoolId(), courseId)
                .stream().map(CourseEnrollment::getStudentId).collect(Collectors.toSet());
        int added = 0;
        for (UUID studentId : requested) {
            if (already.add(studentId)) {
                enrollmentRepository.save(newEnrollment(courseId, studentId));
                added++;
            }
        }
        auditLogService.record("elearning.course.enrolled", "course", courseId, null, Map.of("added", added));
        return new EnrollResult(added, already.size());
    }

    /** Self-enrollment for a learner in a published course open to their class. */
    public CourseView join(UUID courseId) {
        Course course = requirePublished(courseId);
        StudentRef student = requireStudent();
        if (course.getClassGroupId() != null && !isInClass(student.id(), course.getClassGroupId())) {
            throw ApiException.forbidden("course_not_open", "This course is for another class.");
        }
        CourseEnrollment enrollment = enrollmentRepository
                .findBySchoolIdAndCourseIdAndStudentIdAndDeletedAtIsNull(schoolId(), courseId, student.id())
                .orElseGet(() -> enrollmentRepository.save(newEnrollment(courseId, student.id())));
        List<CourseItem> items = itemRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(
                schoolId(), courseId);
        Progress p = progress(course, items, enrollment);
        return toCourseView(course, items.size(), 0, p.progress(), true, enrollment.getCompletedAt());
    }

    public List<LearnerProgressView> courseProgress(UUID courseId) {
        Course course = requireCourse(courseId);
        List<CourseItem> items = itemRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(
                schoolId(), courseId);
        List<CourseEnrollment> enrollments = enrollmentRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNull(
                schoolId(), courseId);
        Map<UUID, StudentRef> students = enrollments.isEmpty() ? Map.of()
                : peopleApi.findStudents(schoolId(), enrollments.stream().map(CourseEnrollment::getStudentId).toList())
                .stream().collect(Collectors.toMap(StudentRef::id, Function.identity(), (a, b) -> a));
        return enrollments.stream().map(e -> {
            Progress p = progress(course, items, e);
            StudentRef s = students.get(e.getStudentId());
            return new LearnerProgressView(e.getId(), e.getStudentId(), s == null ? null : s.fullName(),
                    s == null ? null : s.admissionNumber(), e.getStatus(), p.progress(), p.averageScore(),
                    e.getEnrolledAt(), e.getCompletedAt());
        }).sorted(Comparator.comparing(LearnerProgressView::progress).reversed()).toList();
    }

    public MyCourseView myProgress(UUID courseId) {
        Course course = requireCourse(courseId);
        StudentRef student = requireStudent();
        CourseEnrollment enrollment = enrollmentRepository
                .findBySchoolIdAndCourseIdAndStudentIdAndDeletedAtIsNull(schoolId(), courseId, student.id())
                .orElseThrow(() -> ApiException.forbidden("not_enrolled", "Join the course to see your progress."));
        List<CourseItem> items = itemRepository.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(
                schoolId(), courseId);
        Progress p = progress(course, items, enrollment);
        List<MyItemView> itemViews = items.stream().map(i -> {
            ItemOutcome o = p.outcomes().get(i.getId());
            return new MyItemView(i.getId(), i.getItemType(), i.getRefId(), i.getTitle(), i.isRequired(),
                    o != null && o.completed(), o == null ? null : o.scorePercent());
        }).toList();
        return new MyCourseView(course.getId(), course.getTitle(), enrollment.getStatus(), p.progress(),
                p.averageScore(), enrollment.getCompletedAt(), itemViews);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    record Progress(int progress, Double averageScore, Map<UUID, ItemOutcome> outcomes) {
        static final Progress NONE = new Progress(0, null, Map.of());
    }

    /** Computes progress and records the completion the first time every required item is done. */
    Progress progress(Course course, List<CourseItem> items, CourseEnrollment enrollment) {
        if (items.isEmpty()) {
            return Progress.NONE;
        }
        List<ItemOutcome> outcomes = progressApi.outcomes(schoolId(), enrollment.getStudentId(),
                items.stream().map(i -> new ItemRef(ItemType.valueOf(i.getItemType()), i.getRefId())).toList());
        Map<UUID, ItemOutcome> byItem = new HashMap<>();
        for (int i = 0; i < items.size() && i < outcomes.size(); i++) {
            byItem.put(items.get(i).getId(), outcomes.get(i));
        }
        List<CourseItem> required = items.stream().filter(CourseItem::isRequired).toList();
        List<CourseItem> counted = required.isEmpty() ? items : required;
        long done = counted.stream().filter(i -> byItem.containsKey(i.getId()) && byItem.get(i.getId()).completed()).count();
        int percent = (int) Math.round(done * 100.0 / counted.size());
        Double average = outcomes.stream().map(ItemOutcome::scorePercent).filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().stream().map(v -> Math.round(v * 10) / 10.0).boxed()
                .findFirst().orElse(null);
        if (percent == 100 && "ACTIVE".equals(enrollment.getStatus())) {
            enrollment.setStatus("COMPLETED");
            enrollment.setCompletedAt(Instant.now());
            enrollmentRepository.save(enrollment);
            auditLogService.record("elearning.course.completed", "course_enrollment", enrollment.getId(), null,
                    Map.of("courseId", course.getId(), "studentId", enrollment.getStudentId()));
        }
        return new Progress(percent, average, byItem);
    }

    private void apply(Course course, CourseInput input, boolean creating) {
        if (creating || input.title() != null) {
            if (input.title() == null || input.title().isBlank()) {
                throw ApiException.badRequest("title_required", "A title is required.");
            }
            course.setTitle(input.title().trim());
        }
        if (input.description() != null) {
            course.setDescription(blankToNull(input.description()));
        }
        if (input.subjectId() != null) {
            course.setSubjectId(input.subjectId());
        }
        if (input.classGroupId() != null) {
            course.setClassGroupId(input.classGroupId());
        }
        course.setUpdatedBy(TenantContext.actorUserId());
    }

    private CourseView save(Course course, String auditAction) {
        course.setUpdatedBy(TenantContext.actorUserId());
        Course saved = courseRepository.save(course);
        CourseView view = toCourseView(saved, 0, 0, 0, false, null);
        auditLogService.record(auditAction, "course", saved.getId(), null, view);
        return view;
    }

    private CourseEnrollment newEnrollment(UUID courseId, UUID studentId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setSchoolId(schoolId());
        enrollment.setCourseId(courseId);
        enrollment.setStudentId(studentId);
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setCreatedBy(TenantContext.actorUserId());
        enrollment.setUpdatedBy(TenantContext.actorUserId());
        return enrollment;
    }

    private boolean isInClass(UUID studentId, UUID classGroupId) {
        return peopleApi.listStudentsByClass(schoolId(), classGroupId, null).stream()
                .anyMatch(s -> s.id().equals(studentId));
    }

    private Map<UUID, List<CourseItem>> itemsByCourse(List<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return Map.of();
        }
        return itemRepository.findBySchoolIdAndCourseIdInAndDeletedAtIsNull(schoolId(), courseIds).stream()
                .sorted(Comparator.comparingInt(CourseItem::getPosition))
                .collect(Collectors.groupingBy(CourseItem::getCourseId));
    }

    private StudentRef requireStudent() {
        return peopleApi.studentForUser(schoolId(), SecurityUtils.requireUserId())
                .orElseThrow(() -> ApiException.forbidden("not_a_student", "Only learners can do this."));
    }

    private Course requireCourse(UUID id) {
        return courseRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("course_not_found", "Course not found."));
    }

    private Course requirePublished(UUID id) {
        Course course = requireCourse(id);
        if (!"PUBLISHED".equals(course.getStatus())) {
            throw ApiException.conflict("course_not_published", "Only published courses accept learners.");
        }
        return course;
    }

    private static ItemType parseType(String value) {
        try {
            return ItemType.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("invalid_item_type", "Item type must be RESOURCE, ASSIGNMENT or QUIZ.");
        }
    }

    private ItemView toItemView(CourseItem i) {
        return new ItemView(i.getId(), i.getPosition(), i.getItemType(), i.getRefId(), i.getTitle(), i.isRequired());
    }

    private CourseView toCourseView(Course c, int itemCount, int enrolled, int progress, boolean isEnrolled,
                                    Instant completedAt) {
        return new CourseView(c.getId(), c.getTitle(), c.getDescription(), c.getSubjectId(), c.getClassGroupId(),
                c.getStatus(), c.getPublishedAt(), itemCount, enrolled, progress, isEnrolled, completedAt);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static UUID schoolId() {
        return TenantContext.schoolId();
    }

    public record CourseInput(String title, String description, UUID subjectId, UUID classGroupId) {
    }

    public record CourseView(UUID id, String title, String description, UUID subjectId, UUID classGroupId,
                             String status, Instant publishedAt, int itemCount, int enrolledCount, int progress,
                             boolean enrolled, Instant completedAt) {
    }

    public record ItemView(UUID id, int position, String itemType, UUID refId, String title, boolean required) {
    }

    public record EnrollResult(int added, int enrolled) {
    }

    public record LearnerProgressView(UUID id, UUID studentId, String studentName, String admissionNumber,
                                      String status, int progress, Double averageScore, Instant enrolledAt,
                                      Instant completedAt) {
    }

    public record MyItemView(UUID id, String itemType, UUID refId, String title, boolean required, boolean completed,
                             Double scorePercent) {
    }

    public record MyCourseView(UUID id, String title, String status, int progress, Double averageScore,
                               Instant completedAt, List<MyItemView> items) {
    }
}
