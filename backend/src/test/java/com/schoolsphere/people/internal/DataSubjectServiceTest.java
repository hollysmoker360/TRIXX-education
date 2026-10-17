package com.schoolsphere.people.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.people.internal.DataSubjectService.DataSubjectExport;
import com.schoolsphere.people.internal.DataSubjectService.EraseResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Data-subject export and erasure (spec 3.3.5, 13.3): anonymize rewrites the profile PII this
 * module owns and retains academic records; purge is refused while enrollments exist because they
 * fall under legal retention. app_user anonymization is not reachable through the auth module's
 * public API, so it is reported as a follow-up instead.
 */
class DataSubjectServiceTest {

    private StudentRepository studentRepository;
    private TeacherRepository teacherRepository;
    private ParentProfileRepository parentRepository;
    private GuardianLinkRepository guardianLinkRepository;
    private EnrollmentRepository enrollmentRepository;
    private TeacherAssignmentRepository assignmentRepository;
    private UserDirectory userDirectory;
    private AuditLogService auditLogService;
    private JdbcTemplate jdbcTemplate;
    private DataSubjectService service;

    private UUID schoolId;
    private UUID subjectUserId;

    @BeforeEach
    void setUp() {
        studentRepository = mock(StudentRepository.class);
        teacherRepository = mock(TeacherRepository.class);
        parentRepository = mock(ParentProfileRepository.class);
        guardianLinkRepository = mock(GuardianLinkRepository.class);
        enrollmentRepository = mock(EnrollmentRepository.class);
        assignmentRepository = mock(TeacherAssignmentRepository.class);
        userDirectory = mock(UserDirectory.class);
        auditLogService = mock(AuditLogService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new DataSubjectService(studentRepository, teacherRepository, parentRepository,
                guardianLinkRepository, enrollmentRepository, assignmentRepository, userDirectory,
                auditLogService, jdbcTemplate);

        schoolId = UUID.randomUUID();
        subjectUserId = UUID.randomUUID();
        TenantContext.set(schoolId, UUID.randomUUID(), UUID.randomUUID(), "device-1");

        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(anyString(), eq(Instant.class), any(Object[].class))).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void anonymizeRewritesProfilePiiAndRetainsEnrollments() {
        Student student = student(subjectUserId);
        when(studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(List.of(enrollment(student.getId())));

        EraseResult result = service.erase(subjectUserId, null, "request via support");

        assertThat(result.mode()).isEqualTo("anonymize");
        assertThat(result.subjectUserId()).isEqualTo(subjectUserId.toString());
        assertThat(student.getFirstName()).isEqualTo("Redacted");
        assertThat(student.getLastName()).isEqualTo("User");
        assertThat(student.getOtherNames()).isNull();
        assertThat(student.getDateOfBirth()).isNull();
        assertThat(student.getGender()).isNull();
        assertThat(student.getUpiEncrypted()).isNull();
        assertThat(student.getMedicalNotesEncrypted()).isNull();
        assertThat(student.getPhotoKey()).isNull();
        assertThat(student.getDeletedAt()).isNull();
        assertThat(student.getAdmissionNumber()).isEqualTo("ADM-00001");

        verify(studentRepository).save(student);
        verify(enrollmentRepository, never()).save(any());
        verify(enrollmentRepository, never()).delete(any());
        assertThat(result.retainedForLegalObligation())
                .contains("enrollment", "attendance_events", "grades");
        assertThat(result.actionsTaken())
                .anyMatch(action -> action.startsWith("app_user.pii_not_reachable_via_public_api"));
        assertThat(result.completedAt()).isNotNull();
        verify(auditLogService).record(eq("data_subject.erase"), eq("app_user"), eq(subjectUserId), any(), any());
    }

    @Test
    void anonymizeWipesTeacherAndParentProfilePii() {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setUserId(subjectUserId);
        teacher.setFirstName("John");
        teacher.setLastName("Ochieng");
        teacher.setPhone("+254711000111");
        ParentProfile parent = new ParentProfile();
        parent.setId(UUID.randomUUID());
        parent.setUserId(subjectUserId);
        parent.setFirstName("Mary");
        parent.setLastName("Ochieng");
        parent.setPhoneEncrypted("+254722000222");
        when(teacherRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(teacher));
        when(parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(parent));

        EraseResult result = service.erase(subjectUserId, "anonymize", "erasure request");

        assertThat(result.mode()).isEqualTo("anonymize");
        assertThat(teacher.getPhone()).isNull();
        assertThat(teacher.getFirstName()).isEqualTo("Redacted");
        assertThat(teacher.getDeletedAt()).isNull();
        assertThat(parent.getPhoneEncrypted()).isNull();
        assertThat(parent.getFirstName()).isEqualTo("Redacted");
        assertThat(parent.getLastName()).isEqualTo("Redacted");
        verify(teacherRepository).save(teacher);
        verify(parentRepository).save(parent);
    }

    @Test
    void purgeIsRejectedWithConflictWhenEnrollmentsExist() {
        Student student = student(subjectUserId);
        when(studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(List.of(enrollment(student.getId())));

        assertThatThrownBy(() -> service.erase(subjectUserId, "purge", "court order"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("retention_obligation"));

        verify(studentRepository, never()).save(any());
        verify(auditLogService, never()).record(eq("data_subject.erase"), any(), any(), any(), any());
    }

    @Test
    void purgeSucceedsWhenNoEnrollmentsExist() {
        Student student = student(subjectUserId);
        when(studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(List.of());

        EraseResult result = service.erase(subjectUserId, "purge", "court order");

        assertThat(result.mode()).isEqualTo("purge");
        assertThat(student.getDeletedAt()).isNotNull();
        assertThat(student.getUpiEncrypted()).isNull();
        assertThat(student.getMedicalNotesEncrypted()).isNull();
        assertThat(result.actionsTaken()).contains("person_profiles.soft_deleted");
        verify(studentRepository, times(2)).save(student);
        verify(auditLogService).record(eq("data_subject.erase"), eq("app_user"), eq(subjectUserId), any(), any());
    }

    @Test
    void unknownModeIsRejected() {
        assertThatThrownBy(() -> service.erase(subjectUserId, "shred", "nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("invalid_mode"));
    }

    @Test
    void exportBundlesProfilesRecordsAndAuditSummary() {
        Student student = student(subjectUserId);
        when(studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId))
                .thenReturn(Optional.of(student));
        when(enrollmentRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(List.of(enrollment(student.getId())));
        when(guardianLinkRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(List.of());
        when(userDirectory.findById(subjectUserId)).thenReturn(Optional.of(
                new UserDirectory.UserRef(subjectUserId, "amina@school.example", "Amina Hassan",
                        "+254700000000", "active")));

        DataSubjectExport export = service.export(subjectUserId, null);

        assertThat(export.subjectUserId()).isEqualTo(subjectUserId.toString());
        assertThat(export.email()).isEqualTo("amina@school.example");
        assertThat(export.displayName()).isEqualTo("Amina Hassan");
        assertThat(export.profile()).containsKey("student");
        assertThat(export.enrollments()).hasSize(1);
        assertThat(export.guardianLinks()).isEmpty();
        assertThat(export.teacherAssignments()).isEmpty();
        assertThat(export.auditSummary()).containsEntry("auditEventCount", 0L);
        assertThat(export.exportedAt()).isNotNull();
        verify(auditLogService).record("data_subject.export", "app_user", subjectUserId, null, null);
    }

    @Test
    void exportRejectsAStudentProfileOwnedByAnotherUser() {
        Student student = student(UUID.randomUUID());
        when(studentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, student.getId()))
                .thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.export(subjectUserId, student.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("subject_mismatch"));
    }

    private Student student(UUID userId) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setSchoolId(schoolId);
        student.setUserId(userId);
        student.setAdmissionNumber("ADM-00001");
        student.setFirstName("Amina");
        student.setLastName("Hassan");
        student.setOtherNames("Wanjiru");
        student.setDateOfBirth(LocalDate.of(2010, 3, 4));
        student.setGender("female");
        student.setUpiEncrypted("12345678");
        student.setMedicalNotesEncrypted("asthma inhaler");
        student.setPhotoKey("photos/amina.png");
        return student;
    }

    private Enrollment enrollment(UUID studentId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setSchoolId(schoolId);
        enrollment.setStudentId(studentId);
        enrollment.setAcademicYearId(UUID.randomUUID());
        enrollment.setClassGroupId(UUID.randomUUID());
        enrollment.setStatus("enrolled");
        enrollment.setDecidedAt(Instant.now());
        return enrollment;
    }
}
