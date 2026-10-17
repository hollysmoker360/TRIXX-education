package com.schoolsphere.elearning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.learning.api.LearningProgressApi;
import com.schoolsphere.learning.api.LearningProgressApi.ItemInfo;
import com.schoolsphere.learning.api.LearningProgressApi.ItemOutcome;
import com.schoolsphere.learning.api.LearningProgressApi.ItemType;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.people.api.PeopleApi.StudentRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Course rules: publishing, item validation, class enrollment and completion tracking. */
class ElearningServiceTest {

    private CourseRepository courses;
    private CourseItemRepository items;
    private CourseEnrollmentRepository enrollments;
    private PeopleApi people;
    private LearningProgressApi progress;
    private AuditLogService audit;
    private ElearningService service;
    private UUID schoolId;

    @BeforeEach
    void setUp() {
        courses = mock(CourseRepository.class);
        items = mock(CourseItemRepository.class);
        enrollments = mock(CourseEnrollmentRepository.class);
        people = mock(PeopleApi.class);
        progress = mock(LearningProgressApi.class);
        audit = mock(AuditLogService.class);
        service = new ElearningService(courses, items, enrollments, people, progress, audit);
        schoolId = UUID.randomUUID();
        TenantContext.set(schoolId, UUID.randomUUID(), UUID.randomUUID(), "device-1");
        when(courses.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(items.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(enrollments.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishingAnEmptyCourseIsRejected() {
        Course course = course("DRAFT");
        when(items.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(schoolId, course.getId()))
                .thenReturn(List.of());
        assertThatThrownBy(() -> service.publishCourse(course.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least one");
    }

    @Test
    void addItemUsesTheLearningItemTitleAndRejectsDuplicates() {
        Course course = course("DRAFT");
        UUID quizId = UUID.randomUUID();
        when(progress.findItem(schoolId, ItemType.QUIZ, quizId))
                .thenReturn(Optional.of(new ItemInfo(ItemType.QUIZ, quizId, "Fractions quiz", "published")));
        when(items.findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(schoolId, course.getId()))
                .thenReturn(List.of(item(course, ItemType.RESOURCE, 1, true)));

        var added = service.addItem(course.getId(), "quiz", quizId, null);
        assertThat(added.title()).isEqualTo("Fractions quiz");
        assertThat(added.position()).isEqualTo(2);
        assertThat(added.required()).isTrue();

        when(items.existsBySchoolIdAndCourseIdAndItemTypeAndRefIdAndDeletedAtIsNull(schoolId, course.getId(), "QUIZ",
                quizId)).thenReturn(true);
        assertThatThrownBy(() -> service.addItem(course.getId(), "QUIZ", quizId, true))
                .isInstanceOf(ApiException.class).hasMessageContaining("already");
    }

    @Test
    void addItemRejectsUnknownItems() {
        Course course = course("DRAFT");
        when(progress.findItem(eq(schoolId), eq(ItemType.RESOURCE), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addItem(course.getId(), "RESOURCE", UUID.randomUUID(), true))
                .isInstanceOf(ApiException.class).hasMessageContaining("does not exist");
    }

    @Test
    void enrollingTheClassSkipsLearnersAlreadyEnrolled() {
        Course course = course("PUBLISHED");
        course.setClassGroupId(UUID.randomUUID());
        StudentRef a = student();
        StudentRef b = student();
        when(people.listStudentsByClass(schoolId, course.getClassGroupId(), null)).thenReturn(List.of(a, b));
        CourseEnrollment existing = new CourseEnrollment();
        existing.setStudentId(a.id());
        when(enrollments.findBySchoolIdAndCourseIdAndDeletedAtIsNull(schoolId, course.getId()))
                .thenReturn(List.of(existing));

        var result = service.enroll(course.getId(), null);

        assertThat(result.added()).isEqualTo(1);
        assertThat(result.enrolled()).isEqualTo(2);
        verify(enrollments, times(1)).save(any());
    }

    @Test
    void draftCoursesDoNotAcceptLearners() {
        Course course = course("DRAFT");
        assertThatThrownBy(() -> service.enroll(course.getId(), List.of(UUID.randomUUID())))
                .isInstanceOf(ApiException.class).hasMessageContaining("published");
        verify(enrollments, never()).save(any());
    }

    @Test
    void progressCountsRequiredItemsAndRecordsCompletionOnce() {
        Course course = course("PUBLISHED");
        CourseItem required1 = item(course, ItemType.RESOURCE, 1, true);
        CourseItem required2 = item(course, ItemType.ASSIGNMENT, 2, true);
        CourseItem optional = item(course, ItemType.QUIZ, 3, false);
        CourseEnrollment enrollment = withId(new CourseEnrollment());
        enrollment.setStudentId(UUID.randomUUID());

        when(progress.outcomes(eq(schoolId), eq(enrollment.getStudentId()), anyCollection())).thenReturn(List.of(
                new ItemOutcome(ItemType.RESOURCE, required1.getRefId(), true, null),
                new ItemOutcome(ItemType.ASSIGNMENT, required2.getRefId(), false, null),
                new ItemOutcome(ItemType.QUIZ, optional.getRefId(), true, 80.0)));
        var half = service.progress(course, List.of(required1, required2, optional), enrollment);
        assertThat(half.progress()).isEqualTo(50);
        assertThat(half.averageScore()).isEqualTo(80.0);
        assertThat(enrollment.getStatus()).isEqualTo("ACTIVE");

        when(progress.outcomes(eq(schoolId), eq(enrollment.getStudentId()), anyCollection())).thenReturn(List.of(
                new ItemOutcome(ItemType.RESOURCE, required1.getRefId(), true, null),
                new ItemOutcome(ItemType.ASSIGNMENT, required2.getRefId(), true, 60.0),
                new ItemOutcome(ItemType.QUIZ, optional.getRefId(), false, null)));
        var full = service.progress(course, List.of(required1, required2, optional), enrollment);
        assertThat(full.progress()).isEqualTo(100);
        assertThat(enrollment.getStatus()).isEqualTo("COMPLETED");
        assertThat(enrollment.getCompletedAt()).isNotNull();

        service.progress(course, List.of(required1, required2, optional), enrollment);
        verify(audit, times(1)).record(eq("elearning.course.completed"), any(), any(), any(), any());
    }

    private Course course(String status) {
        Course course = withId(new Course());
        course.setSchoolId(schoolId);
        course.setTitle("Algebra basics");
        course.setStatus(status);
        when(courses.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, course.getId())).thenReturn(Optional.of(course));
        return course;
    }

    private CourseItem item(Course course, ItemType type, int position, boolean required) {
        CourseItem item = withId(new CourseItem());
        item.setCourseId(course.getId());
        item.setItemType(type.name());
        item.setRefId(UUID.randomUUID());
        item.setPosition(position);
        item.setRequired(required);
        item.setTitle(type + " " + position);
        return item;
    }

    private static StudentRef student() {
        return new StudentRef(UUID.randomUUID(), UUID.randomUUID(), "ADM", "Brian", "Mwangi", "active");
    }

    private static <T extends com.schoolsphere.common.persistence.BaseEntity> T withId(T entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return entity;
    }
}
