package com.schoolsphere.people.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.persistence.TenantEntity;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Data-subject access and erasure requests (spec 3.3.5, 13.3), exposed as first-class admin
 * features. Exports bundle everything the people module owns for the subject plus the account
 * reference from the auth module's public {@link UserDirectory}; erasure wipes or anonymizes the
 * profile PII this module owns while academic records (enrollments, attendance, grades) are
 * retained under legal obligation. app_user rows and refresh tokens live behind the auth module's
 * public API, which currently offers no update/erase method; that follow-up is reported in
 * {@link EraseResult#actionsTaken()} rather than reaching into another module's internals.
 */
@Service
@TenantScoped
public class DataSubjectService {

    private static final String MODE_ANONYMIZE = "anonymize";
    private static final String MODE_PURGE = "purge";
    private static final Set<String> MODES = Set.of(MODE_ANONYMIZE, MODE_PURGE);
    private static final List<String> RETAINED_FOR_LEGAL_OBLIGATION =
            List.of("enrollment", "attendance_events", "grades");

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final ParentProfileRepository parentRepository;
    private final GuardianLinkRepository guardianLinkRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherAssignmentRepository assignmentRepository;
    private final UserDirectory userDirectory;
    private final AuditLogService auditLogService;
    private final JdbcTemplate jdbcTemplate;

    public DataSubjectService(StudentRepository studentRepository,
                              TeacherRepository teacherRepository,
                              ParentProfileRepository parentRepository,
                              GuardianLinkRepository guardianLinkRepository,
                              EnrollmentRepository enrollmentRepository,
                              TeacherAssignmentRepository assignmentRepository,
                              UserDirectory userDirectory,
                              AuditLogService auditLogService,
                              JdbcTemplate jdbcTemplate) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.parentRepository = parentRepository;
        this.guardianLinkRepository = guardianLinkRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.userDirectory = userDirectory;
        this.auditLogService = auditLogService;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ----- export (spec 3.3.5) -----

    public DataSubjectExport export(UUID subjectUserId, UUID studentProfileId) {
        UUID schoolId = TenantContext.schoolId();
        Student student = resolveStudent(schoolId, subjectUserId, studentProfileId);
        Teacher teacher = teacherRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId)
                .orElse(null);
        ParentProfile parent = parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId)
                .orElse(null);
        if (student == null && teacher == null && parent == null) {
            throw ApiException.notFound("subject_not_found", "No profile exists for this user in this school.");
        }
        UserDirectory.UserRef user = userDirectory.findById(subjectUserId).orElse(null);

        Map<String, Object> profile = new LinkedHashMap<>();
        if (user != null) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("id", user.id());
            account.put("email", user.email());
            account.put("displayName", user.displayName());
            account.put("phone", user.phone());
            account.put("status", user.status());
            profile.put("user", account);
        }
        if (student != null) {
            profile.put("student", studentProfile(student));
        }
        if (teacher != null) {
            profile.put("teacher", teacherProfile(teacher));
        }
        if (parent != null) {
            profile.put("parent", parentProfile(parent));
        }

        List<Map<String, Object>> enrollments = student == null ? List.of() : enrollmentRepository
                .findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()).stream()
                .map(this::enrollmentRecord)
                .toList();
        List<Map<String, Object>> guardianLinks = new ArrayList<>();
        if (student != null) {
            guardianLinkRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()).stream()
                    .map(this::guardianLinkRecord)
                    .forEach(guardianLinks::add);
        }
        if (parent != null) {
            guardianLinkRepository.findBySchoolIdAndParentIdAndDeletedAtIsNull(schoolId, parent.getId()).stream()
                    .map(this::guardianLinkRecord)
                    .forEach(guardianLinks::add);
        }
        List<Map<String, Object>> teacherAssignments = teacher == null ? List.of() : assignmentRepository
                .findBySchoolIdAndTeacherIdAndDeletedAtIsNull(schoolId, teacher.getId()).stream()
                .map(this::teacherAssignmentRecord)
                .toList();

        List<UUID> trackedIds = new ArrayList<>();
        trackedIds.add(subjectUserId);
        if (student != null) {
            trackedIds.add(student.getId());
        }
        if (teacher != null) {
            trackedIds.add(teacher.getId());
        }
        if (parent != null) {
            trackedIds.add(parent.getId());
        }

        DataSubjectExport export = new DataSubjectExport(subjectUserId.toString(),
                user == null ? null : user.email(),
                user == null ? null : user.displayName(),
                profile, enrollments, guardianLinks, teacherAssignments,
                auditSummary(schoolId, subjectUserId, trackedIds), Instant.now());
        auditLogService.record("data_subject.export", "app_user", subjectUserId, null, null);
        return export;
    }

    // ----- erasure (spec 13.3: erase or anonymize, subject to legal retention) -----

    public EraseResult erase(UUID subjectUserId, String mode, String reason) {
        String resolvedMode = mode == null || mode.isBlank() ? MODE_ANONYMIZE : mode.trim();
        if (!MODES.contains(resolvedMode)) {
            throw ApiException.badRequest("invalid_mode", "Mode must be anonymize or purge.");
        }
        UUID schoolId = TenantContext.schoolId();
        Student student = studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId)
                .orElse(null);
        Teacher teacher = teacherRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId)
                .orElse(null);
        ParentProfile parent = parentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId)
                .orElse(null);
        if (student == null && teacher == null && parent == null) {
            throw ApiException.notFound("subject_not_found", "No profile exists for this user in this school.");
        }

        List<String> actionsTaken = new ArrayList<>();
        if (MODE_PURGE.equals(resolvedMode)) {
            if (student != null && !enrollmentRepository
                    .findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, student.getId()).isEmpty()) {
                throw ApiException.conflict("retention_obligation",
                        "The subject has enrollments; use anonymize so academic records are retained.");
            }
        }

        // Wipe the profile PII this module owns; enrollment/attendance/grade rows are untouched.
        if (student != null) {
            student.setFirstName("Redacted");
            student.setLastName("User");
            student.setOtherNames(null);
            student.setDateOfBirth(null);
            student.setGender(null);
            student.setUpiEncrypted(null);
            student.setMedicalNotesEncrypted(null);
            student.setPhotoKey(null);
            studentRepository.save(student);
            actionsTaken.add("student_profile.pii_wiped");
        }
        if (teacher != null) {
            teacher.setFirstName("Redacted");
            teacher.setLastName("User");
            teacher.setPhone(null);
            teacher.setQualifications(List.of());
            teacherRepository.save(teacher);
            actionsTaken.add("teacher_profile.pii_wiped");
        }
        if (parent != null) {
            parent.setFirstName("Redacted");
            parent.setLastName("Redacted");
            parent.setPhoneEncrypted(null);
            parentRepository.save(parent);
            actionsTaken.add("parent_profile.pii_wiped");
        }

        if (MODE_PURGE.equals(resolvedMode)) {
            if (student != null) {
                softDelete(student);
                studentRepository.save(student);
            }
            if (teacher != null) {
                softDelete(teacher);
                teacherRepository.save(teacher);
            }
            if (parent != null) {
                softDelete(parent);
                parentRepository.save(parent);
            }
            actionsTaken.add("person_profiles.soft_deleted");
        } else {
            actionsTaken.add("person_profiles.retained_with_pii_wiped");
        }

        actionsTaken.add("app_user.pii_not_reachable_via_public_api: displayName/email/phone/passwordHash/"
                + "tokenVersion must be redacted through the auth module's public API (follow-up)");
        actionsTaken.add("auth_tokens.not_revoked_via_public_api: refresh-token revocation requires the auth "
                + "module's public API (follow-up)");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("mode", resolvedMode);
        after.put("reason", reason == null ? "" : reason);
        auditLogService.record("data_subject.erase", "app_user", subjectUserId, null, after);
        return new EraseResult(resolvedMode, subjectUserId.toString(), actionsTaken,
                RETAINED_FOR_LEGAL_OBLIGATION, Instant.now());
    }

    // ----- helpers -----

    private Student resolveStudent(UUID schoolId, UUID subjectUserId, UUID studentProfileId) {
        if (studentProfileId != null) {
            Student student = studentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, studentProfileId)
                    .orElseThrow(() -> ApiException.notFound("student_profile_not_found",
                            "Student profile not found."));
            if (student.getUserId() != null && !student.getUserId().equals(subjectUserId)) {
                throw ApiException.badRequest("subject_mismatch",
                        "The student profile does not belong to the subject user.");
            }
            return student;
        }
        return studentRepository.findBySchoolIdAndUserIdAndDeletedAtIsNull(schoolId, subjectUserId).orElse(null);
    }

    private Map<String, Object> auditSummary(UUID schoolId, UUID subjectUserId, List<UUID> trackedIds) {
        StringBuilder sql = new StringBuilder(
                "SELECT count(*) FROM audit_log WHERE school_id = ? AND (actor_user_id = ? OR entity_id IN (");
        List<Object> params = new ArrayList<>();
        params.add(schoolId);
        params.add(subjectUserId);
        for (int i = 0; i < trackedIds.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
            params.add(trackedIds.get(i));
        }
        sql.append("))");
        Long eventCount = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        String lastSql = sql.toString().replace("count(*)", "max(created_at)");
        Instant lastEventAt = jdbcTemplate.queryForObject(lastSql, Instant.class, params.toArray());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("auditEventCount", eventCount == null ? 0 : eventCount);
        summary.put("lastAuditEventAt", lastEventAt);
        summary.put("trackedIds", trackedIds);
        return summary;
    }

    private Map<String, Object> studentProfile(Student student) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", student.getId());
        map.put("admissionNumber", student.getAdmissionNumber());
        map.put("firstName", student.getFirstName());
        map.put("lastName", student.getLastName());
        map.put("otherNames", student.getOtherNames());
        map.put("dateOfBirth", student.getDateOfBirth());
        map.put("gender", student.getGender());
        map.put("upi", student.getUpiEncrypted());
        map.put("medicalNotes", student.getMedicalNotesEncrypted());
        map.put("photoKey", student.getPhotoKey());
        map.put("status", student.getStatus());
        return map;
    }

    private Map<String, Object> teacherProfile(Teacher teacher) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", teacher.getId());
        map.put("staffNumber", teacher.getStaffNumber());
        map.put("firstName", teacher.getFirstName());
        map.put("lastName", teacher.getLastName());
        map.put("phone", teacher.getPhone());
        map.put("qualifications", teacher.getQualifications());
        map.put("maxWeeklyPeriods", teacher.getMaxWeeklyPeriods());
        return map;
    }

    private Map<String, Object> parentProfile(ParentProfile parent) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", parent.getId());
        map.put("firstName", parent.getFirstName());
        map.put("lastName", parent.getLastName());
        map.put("phone", parent.getPhoneEncrypted());
        return map;
    }

    private Map<String, Object> enrollmentRecord(Enrollment enrollment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", enrollment.getId());
        map.put("academicYearId", enrollment.getAcademicYearId());
        map.put("classGroupId", enrollment.getClassGroupId());
        map.put("streamId", enrollment.getStreamId());
        map.put("status", enrollment.getStatus());
        map.put("decidedAt", enrollment.getDecidedAt());
        map.put("createdAt", enrollment.getCreatedAt());
        return map;
    }

    private Map<String, Object> guardianLinkRecord(GuardianLink link) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", link.getId());
        map.put("parentId", link.getParentId());
        map.put("studentId", link.getStudentId());
        map.put("relationship", link.getRelationship());
        map.put("primary", link.isPrimary());
        map.put("visibilities", link.getVisibilities());
        return map;
    }

    private Map<String, Object> teacherAssignmentRecord(TeacherAssignment assignment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", assignment.getId());
        map.put("classGroupId", assignment.getClassGroupId());
        map.put("streamId", assignment.getStreamId());
        map.put("subjectId", assignment.getSubjectId());
        map.put("termId", assignment.getTermId());
        map.put("periodsPerWeek", assignment.getPeriodsPerWeek());
        return map;
    }

    private void softDelete(TenantEntity entity) {
        entity.setDeletedAt(Instant.now());
        entity.setUpdatedBy(TenantContext.actorUserId());
    }

    public record DataSubjectExport(String subjectUserId, String email, String displayName,
                                    Map<String, Object> profile, List<Map<String, Object>> enrollments,
                                    List<Map<String, Object>> guardianLinks,
                                    List<Map<String, Object>> teacherAssignments,
                                    Map<String, Object> auditSummary, Instant exportedAt) {
    }

    public record EraseResult(String mode, String subjectUserId, List<String> actionsTaken,
                              List<String> retainedForLegalObligation, Instant completedAt) {
    }
}
