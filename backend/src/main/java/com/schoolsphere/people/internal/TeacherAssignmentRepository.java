package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherAssignmentRepository extends JpaRepository<TeacherAssignment, UUID> {

    List<TeacherAssignment> findBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    List<TeacherAssignment> findBySchoolIdAndTeacherIdAndDeletedAtIsNull(UUID schoolId, UUID teacherId);

    List<TeacherAssignment> findBySchoolIdAndTermIdAndDeletedAtIsNull(UUID schoolId, UUID termId);

    List<TeacherAssignment> findBySchoolIdAndClassGroupIdAndTermIdAndDeletedAtIsNull(UUID schoolId, UUID classGroupId,
                                                                                     UUID termId);

    Optional<TeacherAssignment> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    boolean existsBySchoolIdAndTeacherIdAndClassGroupIdAndSubjectIdAndTermIdAndStreamIdAndDeletedAtIsNull(
            UUID schoolId, UUID teacherId, UUID classGroupId, UUID subjectId, UUID termId, UUID streamId);

    boolean existsBySchoolIdAndTeacherIdAndClassGroupIdAndSubjectIdAndTermIdAndStreamIdIsNullAndDeletedAtIsNull(
            UUID schoolId, UUID teacherId, UUID classGroupId, UUID subjectId, UUID termId);
}
