package com.schoolsphere.people.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.InvitationApi;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.persistence.TenantEntity;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Student, teacher, parent, guardian, enrollment, assignment, and grading-scale management. */
@Service
@TenantScoped
public class PeopleService implements PeopleApi {

    private static final Set<String> STUDENT_STATUSES = Set.of("active", "alumni", "left");
    private static final Set<String> ENROLLMENT_STATUSES =
            Set.of("applied", "admitted", "enrolled", "transferred", "withdrawn", "graduated");
    private static final List<String> ACTIVE_ENROLLMENT_STATUSES = List.of("applied", "admitted", "enrolled");
    private static final Set<String> SCALE_KINDS = Set.of("percentage", "letter", "cbc");
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "applied", Set.of("admitted", "withdrawn"),
            "admitted", Set.of("enrolled", "withdrawn"),
            "enrolled", Set.of("transferred", "withdrawn", "graduated"),
            "transferred", Set.of(),
            "withdrawn", Set.of(),
            "graduated", Set.of());

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentProfileRepository parentRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentTransitionRepository transitionRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final GradingScaleRepository gradingScaleRepository;
    private final UserDirectory userDirectory;
    private final InvitationApi invitationApi;
    private final AcademicsApi academicsApi;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    public PeopleService(StudentRepository studentRepository,
                         TeacherRepository teacherRepository,
                         ParentProfileRepository parentRepository,
                         GuardianLinkRepository guardianLinkRepository,
                         EnrollmentRepository enrollmentRepository,
                         EnrollmentTransitionRepository transitionRepository,
                         TeacherAssignmentRepository assignmentRepository,
                         GradingScaleRepository gradingScaleRepository,
                         UserDirectory userDirectory,
                         InvitationApi invitationApi,
                         AcademicsApi academicsApi,
                         AuditLogService auditLogService,
                         JdbcTemplate jdbcTemplate) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.parentRepository = parentRepository;
        this.guardianLinkRepository = guardianLinkRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.transitionRepository = transitionRepository;
        this.assignmentRepository = assignmentRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.userDirectory = userDirectory;
        this.invitationApi = invitationApi;
        this.academicsApi = academicsApi;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ----- students -----

    public PageResponse<StudentView> listStudents(int page, int size, String q, UUID classGroupId, UUID streamId) {
        String like = q == null || q.isBlank() ? null : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("lastName"), Sort.Order.asc("firstName")));
        Page<Student> students = studentRepository.search(schoolId(), like, classGroupId, streamId, pageable);
        return PageResponse.of(students.map(this::toStudentView));
    }

    public StudentView createStudent(UUID userId, String admissionNumber, String firstName, String lastName,
                                     String otherNames, LocalDate dateOfBirth, String gender, String upi,
                                     String medicalNotes) {
        return toStudentView(createStudentEntity(schoolId(), userId, admissionNumber, firstName, lastName, otherNames,
                dateOfBirth, gender, upi, medicalNotes));
    }

    public StudentDetailView getStudent(UUID id) {
        UUID schoolId = schoolId();
        Student student = requireStudent(schoolId, id);
        EnrollmentView enrollment = enrollmentRepository
                .findFirstBySchoolIdAndStudentIdAndStatusInAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId, id,
                        ACTIVE_ENROLLMENT_STATUSES)
                .map(this::toEnrollmentView)
                .orElse(null);
        return new StudentDetailView(toStudentView(student), listGuardians(id), enrollment);
    }

    /** The signed-in learner's own profile with the active enrollment (used by the app). */
    public StudentDetailView myStudentDetail() {
        UUID userId = TenantContext.actorUserId();
        Student student = studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId(), userId)
                .orElseThrow(() -> ApiException.notFound("student_profile_not_found",
                        "No student profile is linked to your account."));
        return getStudent(student.getId());
    }

    public StudentView updateStudent(UUID id, String admissionNumber, String firstName, String lastName,
                                     String otherNames, LocalDate dateOfBirth, String gender, String upi,
                                     String medicalNotes, String status) {
        UUID schoolId = schoolId();
        Student student = requireStudent(schoolId, id);
        if (admissionNumber != null && !admissionNumber.isBlank()
                && !admissionNumber.trim().equals(student.getAdmissionNumber())) {
            if (studentRepository.existsBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(schoolId, admissionNumber.trim())) {
                throw ApiException.conflict("admission_number_taken",
                        "A student with this admission number already exists.");
            }
            student.setAdmissionNumber(admissionNumber.trim());
        }
        if (firstName != null && !firstName.isBlank()) {
            student.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            student.setLastName(lastName);
        }
        if (otherNames != null) {
            student.setOtherNames(otherNames);
        }
        if (dateOfBirth != null) {
            student.setDateOfBirth(dateOfBirth);
        }
        if (gender != null) {
            student.setGender(gender);
        }
        if (upi != null) {
            student.setUpiEncrypted(upi);
        }
        if (medicalNotes != null) {
            student.setMedicalNotesEncrypted(medicalNotes);
        }
        if (status != null && !status.isBlank()) {
            if (!STUDENT_STATUSES.contains(status)) {
                throw ApiException.badRequest("invalid_status", "Status must be active, alumni, or left.");
            }
            student.setStatus(status);
        }
        return toStudentView(studentRepository.save(student));
    }

    public void deleteStudent(UUID id) {
        Student student = requireStudent(schoolId(), id);
        softDelete(student);
        studentRepository.save(student);
    }

    // ----- teachers -----

    public List<TeacherView> listTeachers() {
        UUID schoolId = schoolId();
        List<Teacher> teachers = teacherRepository.findBySchoolIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(schoolId);
        Map<UUID, String> emails = userDirectory
                .findAllByIdIn(teachers.stream().map(Teacher::getUserId).toList())
                .stream().collect(Collectors.toMap(UserDirectory.UserRef::id, UserDirectory.UserRef::email));
        return teachers.stream()
                .map(t -> new TeacherView(t.getId(), t.getUserId(), emails.get(t.getUserId()), t.getStaffNumber(),
                        t.getFirstName(), t.getLastName(), t.getPhone(), t.getMaxWeeklyPeriods()))
                .toList();
    }

    public OnboardedTeacher onboardTeacher(UUID userId, String email, String staffNumber, String firstName,
                                           String lastName, String phone, Integer maxWeeklyPeriods) {
        String temporaryPassword = null;
        UUID resolvedUserId = userId;
        if (resolvedUserId == null) {
            if (email == null || email.isBlank()) {
                throw ApiException.badRequest("email_required", "Provide an email address or an existing user id.");
            }
            UserDirectory.UserRef user = userDirectory.findByEmail(email.trim()).orElse(null);
            if (user == null) {
                UserDirectory.CreatedUser created = userDirectory.createUserWithTemporaryPassword(email.trim(),
                        firstName + " " + lastName, phone);
                user = created.user();
                temporaryPassword = created.temporaryPassword();
            }
            resolvedUserId = user.id();
        }
        Teacher teacher = createTeacherEntity(schoolId(), resolvedUserId, staffNumber, firstName, lastName, phone,
                maxWeeklyPeriods);
        return new OnboardedTeacher(toTeacherView(teacher), temporaryPassword);
    }

    public TeacherView updateTeacher(UUID id, String staffNumber, String firstName, String lastName, String phone,
                                     Integer maxWeeklyPeriods) {
        Teacher teacher = requireTeacher(schoolId(), id);
        if (staffNumber != null) {
            teacher.setStaffNumber(staffNumber);
        }
        if (firstName != null && !firstName.isBlank()) {
            teacher.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            teacher.setLastName(lastName);
        }
        if (phone != null) {
            teacher.setPhone(phone);
        }
        if (maxWeeklyPeriods != null) {
            teacher.setMaxWeeklyPeriods(maxWeeklyPeriods);
        }
        return toTeacherView(teacherRepository.save(teacher));
    }

    // ----- parents and guardian links -----

    public List<ParentView> listParents() {
        UUID schoolId = schoolId();
        List<ParentProfile> parents = parentRepository.findBySchoolIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(schoolId);
        Map<UUID, String> emails = userDirectory
                .findAllByIdIn(parents.stream().map(ParentProfile::getUserId).toList())
                .stream().collect(Collectors.toMap(UserDirectory.UserRef::id, UserDirectory.UserRef::email));
        return parents.stream()
                .map(p -> new ParentView(p.getId(), p.getUserId(), emails.get(p.getUserId()), p.getFirstName(),
                        p.getLastName()))
                .toList();
    }

    public ParentInviteView inviteParent(String email, String firstName, String lastName, String phone, UUID childStudentId) {
        UUID schoolId = schoolId();
        if (childStudentId != null) {
            requireStudent(schoolId, childStudentId);
        }
        String displayName = (firstName + " " + lastName).trim();
        InvitationApi.CreatedInvitation created = invitationApi.createInvitation(
                email,
                "parent",
                schoolId,
                childStudentId,
                TenantContext.actorUserId()
        );

        auditLogService.record("parent.invited", "account_invitation", created.id(), null, Map.of("email", email, "childStudentId", childStudentId));

        return new ParentInviteView(created.id(), email, displayName, childStudentId, created.token(), created.setupUrl());
    }

    public OnboardedParent onboardParent(UUID userId, String email, String firstName, String lastName, String phone) {
        String temporaryPassword = null;
        UUID resolvedUserId = userId;
        if (resolvedUserId == null) {
            if (email == null || email.isBlank()) {
                throw ApiException.badRequest("email_required", "Provide an email address or an existing user id.");
            }
            UserDirectory.UserRef user = userDirectory.findByEmail(email.trim()).orElse(null);
            if (user == null) {
                UserDirectory.CreatedUser created = userDirectory.createUserWithTemporaryPassword(email.trim(),
                        firstName + " " + lastName, phone);
                user = created.user();
                temporaryPassword = created.temporaryPassword();
            }
            resolvedUserId = user.id();
        }
        ParentProfile parent = createParentEntity(schoolId(), resolvedUserId, firstName, lastName, phone);
        return new OnboardedParent(toParentView(parent), temporaryPassword);
    }

    public List<GuardianView> listGuardians(UUID studentId) {
        UUID schoolId = schoolId();
        requireStudent(schoolId, studentId);
        return guardianLinkRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId).stream()
                .map(this::toGuardianView)
                .toList();
    }

    public GuardianView linkGuardian(UUID parentId, UUID studentId, String relationship, Boolean primary,
                                     Map<String, Boolean> visibilities) {
        GuardianLink link = createGuardianLinkEntity(schoolId(), parentId, studentId, relationship,
                Boolean.TRUE.equals(primary), visibilities);
        return toGuardianView(link);
    }

    public GuardianView updateGuardian(UUID id, Map<String, Boolean> visibilities, Boolean primary) {
        GuardianLink link = requireGuardianLink(schoolId(), id);
        if (visibilities != null && !visibilities.isEmpty()) {
            Map<String, Boolean> merged = new LinkedHashMap<>(
                    link.getVisibilities() == null ? Map.of() : link.getVisibilities());
            merged.putAll(visibilities);
            link.setVisibilities(merged);
        }
        if (primary != null) {
            link.setPrimary(primary);
        }
        return toGuardianView(guardianLinkRepository.save(link));
    }

    // ----- enrollments -----

    public List<EnrollmentView> listEnrollments(UUID academicYearId, UUID classGroupId) {
        UUID schoolId = schoolId();
        List<Enrollment> enrollments;
        if (academicYearId != null && classGroupId != null) {
            enrollments = enrollmentRepository.findBySchoolIdAndAcademicYearIdAndClassGroupIdAndDeletedAtIsNull(
                    schoolId, academicYearId, classGroupId);
        } else if (academicYearId != null) {
            enrollments = enrollmentRepository.findBySchoolIdAndAcademicYearIdAndDeletedAtIsNull(schoolId, academicYearId);
        } else if (classGroupId != null) {
            enrollments = enrollmentRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(schoolId, classGroupId);
        } else {
            enrollments = enrollmentRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId);
        }
        return enrollments.stream().map(this::toEnrollmentView).toList();
    }

    public EnrollmentView createEnrollment(UUID studentId, UUID academicYearId, UUID classGroupId, UUID streamId,
                                           String status) {
        String resolved = status == null || status.isBlank() ? "applied" : status;
        return toEnrollmentView(
                createEnrollmentEntity(schoolId(), studentId, academicYearId, classGroupId, streamId, resolved));
    }

    public EnrollmentView transitionEnrollment(UUID id, String toStatus, String reason) {
        Enrollment enrollment = requireEnrollment(schoolId(), id);
        String fromStatus = enrollment.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(fromStatus, Set.of()).contains(toStatus)) {
            throw ApiException.badRequest("invalid_transition",
                    "An enrollment cannot move from " + fromStatus + " to " + toStatus + ".");
        }
        enrollment.setStatus(toStatus);
        enrollment.setDecidedAt(Instant.now());
        enrollment = enrollmentRepository.save(enrollment);
        recordTransition(enrollment, fromStatus, toStatus, reason);
        return toEnrollmentView(enrollment);
    }

    // ----- teacher assignments -----

    public List<AssignmentView> listTeacherAssignments(UUID termId, UUID teacherId) {
        UUID schoolId = schoolId();
        List<TeacherAssignment> assignments;
        if (teacherId != null) {
            assignments = assignmentRepository.findBySchoolIdAndTeacherIdAndDeletedAtIsNull(schoolId, teacherId);
            if (termId != null) {
                assignments = assignments.stream().filter(a -> termId.equals(a.getTermId())).toList();
            }
        } else if (termId != null) {
            assignments = assignmentRepository.findBySchoolIdAndTermIdAndDeletedAtIsNull(schoolId, termId);
        } else {
            assignments = assignmentRepository.findBySchoolIdAndDeletedAtIsNull(schoolId);
        }
        return toAssignmentViews(assignments);
    }

    public AssignmentView createTeacherAssignment(UUID teacherId, UUID classGroupId, UUID streamId, UUID subjectId,
                                                  UUID termId, Integer periodsPerWeek) {
        UUID schoolId = schoolId();
        requireTeacher(schoolId, teacherId);
        if (academicsApi.findClass(schoolId, classGroupId).isEmpty()) {
            throw ApiException.notFound("class_not_found", "Class not found.");
        }
        if (academicsApi.findSubject(schoolId, subjectId).isEmpty()) {
            throw ApiException.notFound("subject_not_found", "Subject not found.");
        }
        boolean exists = streamId == null
                ? assignmentRepository.existsBySchoolIdAndTeacherIdAndClassGroupIdAndSubjectIdAndTermIdAndStreamIdIsNullAndDeletedAtIsNull(
                        schoolId, teacherId, classGroupId, subjectId, termId)
                : assignmentRepository.existsBySchoolIdAndTeacherIdAndClassGroupIdAndSubjectIdAndTermIdAndStreamIdAndDeletedAtIsNull(
                        schoolId, teacherId, classGroupId, subjectId, termId, streamId);
        if (exists) {
            throw ApiException.conflict("assignment_exists",
                    "This teacher already has this assignment for the term.");
        }
        TeacherAssignment assignment = new TeacherAssignment();
        assignment.setSchoolId(schoolId);
        assignment.setTeacherId(teacherId);
        assignment.setClassGroupId(classGroupId);
        assignment.setStreamId(streamId);
        assignment.setSubjectId(subjectId);
        assignment.setTermId(termId);
        assignment.setPeriodsPerWeek(periodsPerWeek);
        assignment = assignmentRepository.save(assignment);
        return toAssignmentViews(List.of(assignment)).get(0);
    }

    public void deleteTeacherAssignment(UUID id) {
        TeacherAssignment assignment = requireAssignment(schoolId(), id);
        softDelete(assignment);
        assignmentRepository.save(assignment);
    }

    // ----- grading scales -----

    public List<GradingScaleView> listGradingScales() {
        return gradingScaleRepository.findBySchoolIdAndDeletedAtIsNullOrderByNameAsc(schoolId()).stream()
                .map(this::toGradingScaleView)
                .toList();
    }

    public GradingScaleView createGradingScale(String name, String kind, List<Map<String, Object>> bands,
                                               Boolean isDefault) {
        return toGradingScaleView(createGradingScaleEntity(schoolId(), name, kind, bands,
                Boolean.TRUE.equals(isDefault)));
    }

    public GradingScaleView updateGradingScale(UUID id, String name, List<Map<String, Object>> bands,
                                               Boolean isDefault) {
        UUID schoolId = schoolId();
        GradingScale scale = requireGradingScale(schoolId, id);
        if (name != null && !name.isBlank() && !name.trim().equals(scale.getName())) {
            if (gradingScaleRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId, name.trim())) {
                throw ApiException.conflict("scale_exists", "A grading scale with this name already exists.");
            }
            scale.setName(name.trim());
        }
        if (bands != null) {
            scale.setBands(bands);
        }
        if (Boolean.TRUE.equals(isDefault)) {
            clearDefaultScale(schoolId);
            scale.setDefault(true);
        } else if (Boolean.FALSE.equals(isDefault)) {
            scale.setDefault(false);
        }
        return toGradingScaleView(gradingScaleRepository.save(scale));
    }

    public List<GradingScaleView> seedDefaults() {
        UUID schoolId = schoolId();
        if (!gradingScaleRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId, "Percentage")) {
            createGradingScaleEntity(schoolId, "Percentage", "percentage", percentageBands(), false);
        }
        if (!gradingScaleRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId, "CBC")) {
            createGradingScaleEntity(schoolId, "CBC", "cbc", cbcBands(), true);
        }
        return listGradingScales();
    }

    // ----- parent-facing -----

    public StudentView myStudent() {
        UUID userId = TenantContext.actorUserId();
        Student student = studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId(), userId)
                .orElseThrow(() -> ApiException.notFound("student_profile_not_found",
                        "No student profile is linked to your account."));
        return toStudentView(student);
    }

    public List<ChildView> myChildren() {
        return childrenOfParent(schoolId(), TenantContext.actorUserId()).stream()
                .map(child -> new ChildView(child.student().id(), child.student().admissionNumber(),
                        child.student().firstName(), child.student().lastName(), child.relationship(),
                        child.visibilities()))
                .toList();
    }

    // ----- PeopleApi (other modules) -----

    @Override
    public java.util.Optional<StudentRef> findStudent(UUID schoolId, UUID studentId) {
        return studentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, studentId).map(this::toStudentRef);
    }

    @Override
    public List<StudentRef> findStudents(UUID schoolId, List<UUID> studentIds) {
        return studentRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId, studentIds).stream()
                .map(this::toStudentRef)
                .toList();
    }

    @Override
    public List<StudentRef> listStudentsByClass(UUID schoolId, UUID classGroupId, UUID streamId) {
        List<UUID> studentIds = enrollmentRepository
                .findBySchoolIdAndClassGroupIdAndStatusInAndDeletedAtIsNull(schoolId, classGroupId,
                        ACTIVE_ENROLLMENT_STATUSES)
                .stream()
                .filter(e -> streamId == null || streamId.equals(e.getStreamId()))
                .map(Enrollment::getStudentId)
                .distinct()
                .toList();
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return studentRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId, studentIds).stream()
                .sorted(Comparator.comparing(Student::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Student::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toStudentRef)
                .toList();
    }

    @Override
    public java.util.Optional<StudentRef> studentForUser(UUID schoolId, UUID userId) {
        return studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId).map(this::toStudentRef);
    }

    @Override
    public java.util.Optional<TeacherRef> teacherForUser(UUID schoolId, UUID userId) {
        return teacherRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId).map(this::toTeacherRef);
    }

    @Override
    public java.util.Optional<TeacherRef> teacherForMembership(UUID schoolId, UUID membershipId) {
        // Read-model exception: memberships live in the tenants module; RLS keeps the lookup tenant-safe.
        List<UUID> userIds = jdbcTemplate.queryForList(
                "SELECT user_id FROM school_membership WHERE school_id = ? AND id = ? AND deleted_at IS NULL",
                UUID.class, schoolId, membershipId);
        if (userIds.isEmpty()) {
            return java.util.Optional.empty();
        }
        return teacherForUser(schoolId, userIds.get(0));
    }

    @Override
    public List<ChildRef> childrenOfParent(UUID schoolId, UUID parentUserId) {
        ParentProfile parent = parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, parentUserId)
                .orElse(null);
        if (parent == null) {
            return List.of();
        }
        List<GuardianLink> links = guardianLinkRepository
                .findBySchoolIdAndParentIdAndDeletedAtIsNull(schoolId, parent.getId());
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, Student> students = studentRepository
                .findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId,
                        links.stream().map(GuardianLink::getStudentId).distinct().toList())
                .stream().collect(Collectors.toMap(Student::getId, Function.identity()));
        return links.stream()
                .filter(link -> students.containsKey(link.getStudentId()))
                .map(link -> new ChildRef(toStudentRef(students.get(link.getStudentId())), link.getRelationship(),
                        link.getVisibilities() == null ? Map.of() : link.getVisibilities()))
                .toList();
    }

    @Override
    public java.util.Optional<ParentRef> parentForUser(UUID schoolId, UUID userId) {
        return parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId).map(this::toParentRef);
    }

    @Override
    public boolean parentCanView(UUID schoolId, UUID parentUserId, UUID studentId, String visibility) {
        ParentProfile parent = parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, parentUserId)
                .orElse(null);
        if (parent == null) {
            return false;
        }
        return guardianLinkRepository
                .findBySchoolIdAndParentIdAndStudentIdAndDeletedAtIsNull(schoolId, parent.getId(), studentId)
                .map(link -> link.getVisibilities() != null
                        && Boolean.TRUE.equals(link.getVisibilities().get(visibility)))
                .orElse(false);
    }

    @Override
    public List<ParentRef> guardiansOfStudent(UUID schoolId, UUID studentId) {
        return guardianLinkRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId).stream()
                .map(link -> parentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, link.getParentId())
                        .map(this::toParentRef)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<TeacherAssignmentRef> assignmentsForTerm(UUID schoolId, UUID termId) {
        return assignmentRepository.findBySchoolIdAndTermIdAndDeletedAtIsNull(schoolId, termId).stream()
                .map(a -> new TeacherAssignmentRef(a.getId(), a.getTeacherId(),
                        teacherRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, a.getTeacherId())
                                .map(Teacher::getUserId)
                                .orElse(null),
                        a.getClassGroupId(), a.getStreamId(), a.getSubjectId(), a.getTermId(),
                        a.getPeriodsPerWeek()))
                .toList();
    }

    @Override
    public StudentRef createStudent(UUID schoolId, UUID userId, String admissionNumber, String firstName,
                                    String lastName, String otherNames, LocalDate dateOfBirth, String gender,
                                    String upi, String medicalNotes) {
        return toStudentRef(createStudentEntity(schoolId, userId, admissionNumber, firstName, lastName, otherNames,
                dateOfBirth, gender, upi, medicalNotes));
    }

    @Override
    public TeacherRef createTeacher(UUID schoolId, UUID userId, String staffNumber, String firstName, String lastName,
                                    String phone, Integer maxWeeklyPeriods) {
        return toTeacherRef(createTeacherEntity(schoolId, userId, staffNumber, firstName, lastName, phone,
                maxWeeklyPeriods));
    }

    @Override
    public ParentRef createParent(UUID schoolId, UUID userId, String firstName, String lastName, String phone) {
        return toParentRef(createParentEntity(schoolId, userId, firstName, lastName, phone));
    }

    @Override
    public UUID createGuardianLink(UUID schoolId, UUID parentId, UUID studentId, String relationship, boolean primary,
                                   Map<String, Boolean> visibilities) {
        return createGuardianLinkEntity(schoolId, parentId, studentId, relationship, primary, visibilities).getId();
    }

    @Override
    public UUID enrollStudent(UUID schoolId, UUID studentId, UUID academicYearId, UUID classGroupId, UUID streamId,
                              String initialStatus) {
        String status = initialStatus == null || initialStatus.isBlank() ? "applied" : initialStatus;
        Enrollment enrollment = createEnrollmentEntity(schoolId, studentId, academicYearId, classGroupId, streamId,
                status);
        if (!"applied".equals(status)) {
            recordTransition(enrollment, null, status, null);
        }
        return enrollment.getId();
    }

    // ----- helpers -----

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private Student requireStudent(UUID schoolId, UUID id) {
        return studentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("student_not_found", "Student not found."));
    }

    private Teacher requireTeacher(UUID schoolId, UUID id) {
        return teacherRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("teacher_not_found", "Teacher not found."));
    }

    private ParentProfile requireParent(UUID schoolId, UUID id) {
        return parentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("parent_not_found", "Parent not found."));
    }

    private GuardianLink requireGuardianLink(UUID schoolId, UUID id) {
        return guardianLinkRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("guardian_link_not_found", "Guardian link not found."));
    }

    private Enrollment requireEnrollment(UUID schoolId, UUID id) {
        return enrollmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("enrollment_not_found", "Enrollment not found."));
    }

    private TeacherAssignment requireAssignment(UUID schoolId, UUID id) {
        return assignmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("assignment_not_found", "Teacher assignment not found."));
    }

    private GradingScale requireGradingScale(UUID schoolId, UUID id) {
        return gradingScaleRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                .orElseThrow(() -> ApiException.notFound("scale_not_found", "Grading scale not found."));
    }

    private Student createStudentEntity(UUID schoolId, UUID userId, String admissionNumber, String firstName,
                                        String lastName, String otherNames, LocalDate dateOfBirth, String gender,
                                        String upi, String medicalNotes) {
        String number = admissionNumber == null || admissionNumber.isBlank()
                ? generateAdmissionNumber(schoolId)
                : admissionNumber.trim();
        if (studentRepository.existsBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(schoolId, number)) {
            throw ApiException.conflict("admission_number_taken",
                    "A student with this admission number already exists.");
        }
        Student student = new Student();
        student.setSchoolId(schoolId);
        student.setUserId(userId);
        student.setAdmissionNumber(number);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setOtherNames(otherNames);
        student.setDateOfBirth(dateOfBirth);
        student.setGender(gender);
        student.setUpiEncrypted(upi);
        student.setMedicalNotesEncrypted(medicalNotes);
        return studentRepository.save(student);
    }

    private String generateAdmissionNumber(UUID schoolId) {
        long count = studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId);
        for (long sequence = count + 1; ; sequence++) {
            String candidate = "ADM-%05d".formatted(sequence);
            if (!studentRepository.existsBySchoolIdAndAdmissionNumberAndDeletedAtIsNull(schoolId, candidate)) {
                return candidate;
            }
        }
    }

    private Teacher createTeacherEntity(UUID schoolId, UUID userId, String staffNumber, String firstName,
                                        String lastName, String phone, Integer maxWeeklyPeriods) {
        if (userId == null) {
            throw ApiException.badRequest("user_required", "A user account is required for a teacher profile.");
        }
        if (userDirectory.findById(userId).isEmpty()) {
            throw ApiException.notFound("user_not_found", "User account not found.");
        }
        if (teacherRepository.existsBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId)) {
            throw ApiException.conflict("teacher_exists", "This user already has a teacher profile in this school.");
        }
        Teacher teacher = new Teacher();
        teacher.setSchoolId(schoolId);
        teacher.setUserId(userId);
        teacher.setStaffNumber(staffNumber);
        teacher.setFirstName(firstName);
        teacher.setLastName(lastName);
        teacher.setPhone(phone);
        teacher.setMaxWeeklyPeriods(maxWeeklyPeriods);
        return teacherRepository.save(teacher);
    }

    private ParentProfile createParentEntity(UUID schoolId, UUID userId, String firstName, String lastName,
                                             String phone) {
        if (userId == null) {
            throw ApiException.badRequest("user_required", "A user account is required for a parent profile.");
        }
        if (userDirectory.findById(userId).isEmpty()) {
            throw ApiException.notFound("user_not_found", "User account not found.");
        }
        if (parentRepository.existsBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, userId)) {
            throw ApiException.conflict("parent_exists", "This user already has a parent profile in this school.");
        }
        ParentProfile parent = new ParentProfile();
        parent.setSchoolId(schoolId);
        parent.setUserId(userId);
        parent.setFirstName(firstName);
        parent.setLastName(lastName);
        parent.setPhoneEncrypted(phone);
        return parentRepository.save(parent);
    }

    private GuardianLink createGuardianLinkEntity(UUID schoolId, UUID parentId, UUID studentId, String relationship,
                                                  boolean primary, Map<String, Boolean> visibilities) {
        requireParent(schoolId, parentId);
        requireStudent(schoolId, studentId);
        if (guardianLinkRepository.existsBySchoolIdAndParentIdAndStudentIdAndDeletedAtIsNull(schoolId, parentId,
                studentId)) {
            throw ApiException.conflict("guardian_link_exists", "This parent is already linked to the student.");
        }
        GuardianLink link = new GuardianLink();
        link.setSchoolId(schoolId);
        link.setParentId(parentId);
        link.setStudentId(studentId);
        link.setRelationship(relationship);
        link.setPrimary(primary);
        link.setVisibilities(withDefaultVisibilities(visibilities));
        return guardianLinkRepository.save(link);
    }

    private static Map<String, Boolean> withDefaultVisibilities(Map<String, Boolean> visibilities) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("attendance", true);
        result.put("grades", true);
        result.put("communication", true);
        if (visibilities != null) {
            result.putAll(visibilities);
        }
        return result;
    }

    private Enrollment createEnrollmentEntity(UUID schoolId, UUID studentId, UUID academicYearId, UUID classGroupId,
                                              UUID streamId, String status) {
        if (!ENROLLMENT_STATUSES.contains(status)) {
            throw ApiException.badRequest("invalid_status", "Unknown enrollment status: " + status);
        }
        requireStudent(schoolId, studentId);
        if (academicsApi.findClass(schoolId, classGroupId).isEmpty()) {
            throw ApiException.notFound("class_not_found", "Class not found.");
        }
        if (enrollmentRepository.findBySchoolIdAndStudentIdAndAcademicYearIdAndDeletedAtIsNull(schoolId, studentId,
                academicYearId).isPresent()) {
            throw ApiException.conflict("enrollment_exists",
                    "This student already has an enrollment for the academic year.");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setSchoolId(schoolId);
        enrollment.setStudentId(studentId);
        enrollment.setAcademicYearId(academicYearId);
        enrollment.setClassGroupId(classGroupId);
        enrollment.setStreamId(streamId);
        enrollment.setStatus(status);
        if (!"applied".equals(status)) {
            enrollment.setDecidedAt(Instant.now());
        }
        return enrollmentRepository.save(enrollment);
    }

    private void recordTransition(Enrollment enrollment, String fromStatus, String toStatus, String reason) {
        EnrollmentTransition transition = new EnrollmentTransition();
        transition.setSchoolId(enrollment.getSchoolId());
        transition.setEnrollmentId(enrollment.getId());
        transition.setFromStatus(fromStatus);
        transition.setToStatus(toStatus);
        transition.setReason(reason);
        transition.setActorMembershipId(TenantContext.actorMembershipId());
        transitionRepository.save(transition);

        Map<String, Object> before = new LinkedHashMap<>();
        if (fromStatus != null) {
            before.put("status", fromStatus);
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", toStatus);
        if (reason != null) {
            after.put("reason", reason);
        }
        auditLogService.record("enrollment.transition", "enrollment", enrollment.getId(), before, after);
    }

    private GradingScale createGradingScaleEntity(UUID schoolId, String name, String kind,
                                                  List<Map<String, Object>> bands, boolean isDefault) {
        String resolvedKind = kind == null || kind.isBlank() ? "percentage" : kind;
        if (!SCALE_KINDS.contains(resolvedKind)) {
            throw ApiException.badRequest("invalid_kind", "Kind must be percentage, letter, or cbc.");
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("name_required", "A grading scale name is required.");
        }
        if (gradingScaleRepository.existsBySchoolIdAndNameAndDeletedAtIsNull(schoolId, name.trim())) {
            throw ApiException.conflict("scale_exists", "A grading scale with this name already exists.");
        }
        if (isDefault) {
            clearDefaultScale(schoolId);
        }
        GradingScale scale = new GradingScale();
        scale.setSchoolId(schoolId);
        scale.setName(name.trim());
        scale.setKind(resolvedKind);
        scale.setBands(bands == null ? List.of() : bands);
        scale.setDefault(isDefault);
        return gradingScaleRepository.save(scale);
    }

    private void clearDefaultScale(UUID schoolId) {
        gradingScaleRepository.findFirstBySchoolIdAndIsDefaultTrueAndDeletedAtIsNull(schoolId).ifPresent(existing -> {
            existing.setDefault(false);
            gradingScaleRepository.save(existing);
        });
    }

    private static List<Map<String, Object>> percentageBands() {
        return List.of(band(0, 39, "E", "Below expectations"), band(40, 49, "D", "Approaching expectations"),
                band(50, 59, "C", "Meets some expectations"), band(60, 79, "B", "Meets expectations"),
                band(80, 100, "A", "Exceeds expectations"));
    }

    private static List<Map<String, Object>> cbcBands() {
        return List.of(band(80, 100, "Exceeding Expectation", "Learner exceeds the expected level."),
                band(65, 79, "Meeting", "Learner meets the expected level."),
                band(50, 64, "Approaching", "Learner is approaching the expected level."),
                band(0, 49, "Below", "Learner is below the expected level."));
    }

    private static Map<String, Object> band(int min, int max, String grade, String descriptor) {
        Map<String, Object> band = new LinkedHashMap<>();
        band.put("min", min);
        band.put("max", max);
        band.put("grade", grade);
        band.put("descriptor", descriptor);
        return band;
    }

    private List<AssignmentView> toAssignmentViews(List<TeacherAssignment> assignments) {
        if (assignments.isEmpty()) {
            return List.of();
        }
        UUID schoolId = schoolId();
        Map<UUID, String> teacherNames = teacherRepository
                .findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId,
                        assignments.stream().map(TeacherAssignment::getTeacherId).distinct().toList())
                .stream().collect(Collectors.toMap(Teacher::getId, t -> t.getFirstName() + " " + t.getLastName()));
        Map<UUID, String> classNames = academicsApi
                .findClasses(schoolId, assignments.stream().map(TeacherAssignment::getClassGroupId).distinct().toList())
                .stream().collect(Collectors.toMap(AcademicsApi.ClassInfo::id, AcademicsApi.ClassInfo::name));
        Map<UUID, String> subjectNames = academicsApi
                .findSubjects(schoolId, assignments.stream().map(TeacherAssignment::getSubjectId).distinct().toList())
                .stream().collect(Collectors.toMap(AcademicsApi.SubjectInfo::id, AcademicsApi.SubjectInfo::name));
        return assignments.stream()
                .map(a -> new AssignmentView(a.getId(), a.getTeacherId(), teacherNames.get(a.getTeacherId()),
                        a.getClassGroupId(), classNames.get(a.getClassGroupId()), a.getStreamId(), a.getSubjectId(),
                        subjectNames.get(a.getSubjectId()), a.getTermId(), a.getPeriodsPerWeek()))
                .toList();
    }

    private StudentRef toStudentRef(Student student) {
        return new StudentRef(student.getId(), student.getUserId(), student.getAdmissionNumber(),
                student.getFirstName(), student.getLastName(), student.getStatus());
    }

    private TeacherRef toTeacherRef(Teacher teacher) {
        return new TeacherRef(teacher.getId(), teacher.getUserId(), teacher.getStaffNumber(), teacher.getFirstName(),
                teacher.getLastName());
    }

    private ParentRef toParentRef(ParentProfile parent) {
        return new ParentRef(parent.getId(), parent.getUserId(), parent.getFirstName(), parent.getLastName());
    }

    private StudentView toStudentView(Student student) {
        return new StudentView(student.getId(), student.getUserId(), student.getAdmissionNumber(),
                student.getFirstName(), student.getLastName(), student.getOtherNames(), student.getDateOfBirth(),
                student.getGender(), student.getPhotoKey(), student.getStatus());
    }

    private TeacherView toTeacherView(Teacher teacher) {
        String email = userDirectory.findById(teacher.getUserId()).map(UserDirectory.UserRef::email).orElse(null);
        return new TeacherView(teacher.getId(), teacher.getUserId(), email, teacher.getStaffNumber(),
                teacher.getFirstName(), teacher.getLastName(), teacher.getPhone(), teacher.getMaxWeeklyPeriods());
    }

    private ParentView toParentView(ParentProfile parent) {
        String email = userDirectory.findById(parent.getUserId()).map(UserDirectory.UserRef::email).orElse(null);
        return new ParentView(parent.getId(), parent.getUserId(), email, parent.getFirstName(), parent.getLastName());
    }

    private GuardianView toGuardianView(GuardianLink link) {
        String parentName = parentRepository
                .findBySchoolIdAndIdAndDeletedAtIsNull(link.getSchoolId(), link.getParentId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(null);
        return new GuardianView(link.getId(), link.getParentId(), parentName, link.getStudentId(),
                link.getRelationship(), link.isPrimary(), link.getVisibilities() == null ? Map.of() : link.getVisibilities());
    }

    private EnrollmentView toEnrollmentView(Enrollment enrollment) {
        return new EnrollmentView(enrollment.getId(), enrollment.getStudentId(), enrollment.getAcademicYearId(),
                enrollment.getClassGroupId(), enrollment.getStreamId(), enrollment.getStatus(),
                enrollment.getDecidedAt());
    }

    private GradingScaleView toGradingScaleView(GradingScale scale) {
        return new GradingScaleView(scale.getId(), scale.getName(), scale.getKind(), scale.getBands(),
                scale.isDefault());
    }

    private void softDelete(TenantEntity entity) {
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(TenantContext.actorUserId());
    }

    public record StudentView(UUID id, UUID userId, String admissionNumber, String firstName, String lastName,
                              String otherNames, LocalDate dateOfBirth, String gender, String photoKey,
                              String status) {
    }

    public record StudentDetailView(StudentView student, List<GuardianView> guardianLinks,
                                    EnrollmentView enrollment) {
    }

    public record TeacherView(UUID id, UUID userId, String email, String staffNumber, String firstName,
                              String lastName, String phone, Integer maxWeeklyPeriods) {
    }

    public record OnboardedTeacher(TeacherView teacher, String temporaryPassword) {
    }

    public record ParentView(UUID id, UUID userId, String email, String firstName, String lastName) {
    }

    public record ParentInviteView(UUID id, String email, String displayName, UUID childStudentId, String token, String setupUrl) {
    }

    public record OnboardedParent(ParentView parent, String temporaryPassword) {
    }

    public record GuardianView(UUID id, UUID parentId, String parentName, UUID studentId, String relationship,
                               boolean primary, Map<String, Boolean> visibilities) {
    }

    public record EnrollmentView(UUID id, UUID studentId, UUID academicYearId, UUID classGroupId, UUID streamId,
                                 String status, Instant decidedAt) {
    }

    public record AssignmentView(UUID id, UUID teacherId, String teacherName, UUID classGroupId,
                                 String classGroupName, UUID streamId, UUID subjectId, String subjectName,
                                 UUID termId, Integer periodsPerWeek) {
    }

    public record GradingScaleView(UUID id, String name, String kind, List<Map<String, Object>> bands,
                                   boolean isDefault) {
    }

    public record ChildView(UUID id, String admissionNumber, String firstName, String lastName, String relationship,
                            Map<String, Boolean> visibilities) {
    }
}
