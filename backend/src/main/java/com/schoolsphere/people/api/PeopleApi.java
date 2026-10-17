package com.schoolsphere.people.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Public API of the people module (spec 4.4). */
public interface PeopleApi {

    Optional<StudentRef> findStudent(UUID schoolId, UUID studentId);

    List<StudentRef> findStudents(UUID schoolId, List<UUID> studentIds);

    List<StudentRef> listStudentsByClass(UUID schoolId, UUID classGroupId, UUID streamId);

    Optional<StudentRef> studentForUser(UUID schoolId, UUID userId);

    Optional<TeacherRef> teacherForUser(UUID schoolId, UUID userId);

    Optional<TeacherRef> teacherForMembership(UUID schoolId, UUID membershipId);

    List<ChildRef> childrenOfParent(UUID schoolId, UUID parentUserId);

    Optional<ParentRef> parentForUser(UUID schoolId, UUID userId);

    /** Guardian check with the requested visibility flag (attendance, grades, communication). */
    boolean parentCanView(UUID schoolId, UUID parentUserId, UUID studentId, String visibility);

    /** Parents/guardians linked to a student, used for absence alerts and reports. */
    List<ParentRef> guardiansOfStudent(UUID schoolId, UUID studentId);

    /** Teacher-to-class/subject assignments for a term (timetable generation input, spec 12.1). */
    List<TeacherAssignmentRef> assignmentsForTerm(UUID schoolId, UUID termId);

    StudentRef createStudent(UUID schoolId, UUID userId, String admissionNumber, String firstName,
                             String lastName, String otherNames, java.time.LocalDate dateOfBirth, String gender,
                             String upi, String medicalNotes);

    TeacherRef createTeacher(UUID schoolId, UUID userId, String staffNumber, String firstName, String lastName,
                             String phone, Integer maxWeeklyPeriods);

    ParentRef createParent(UUID schoolId, UUID userId, String firstName, String lastName, String phone);

    UUID createGuardianLink(UUID schoolId, UUID parentId, UUID studentId, String relationship, boolean primary,
                            Map<String, Boolean> visibilities);

    UUID enrollStudent(UUID schoolId, UUID studentId, UUID academicYearId, UUID classGroupId, UUID streamId,
                       String initialStatus);

    record StudentRef(UUID id, UUID userId, String admissionNumber, String firstName, String lastName,
                      String status) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    record TeacherRef(UUID id, UUID userId, String staffNumber, String firstName, String lastName) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    record ParentRef(UUID id, UUID userId, String firstName, String lastName) {

        public String fullName() {
            return firstName + " " + lastName;
        }
    }

    record TeacherAssignmentRef(UUID id, UUID teacherId, UUID userId, UUID classGroupId, UUID streamId,
                                UUID subjectId, UUID termId, Integer periodsPerWeek) {
    }

    record ChildRef(StudentRef student, String relationship, Map<String, Boolean> visibilities) {
    }
}
