package com.schoolsphere.grades.internal.repository;

import com.schoolsphere.grades.internal.entity.Assessment;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Assessment> findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(
            UUID schoolId, UUID classGroupId);

    List<Assessment> findBySchoolIdAndClassGroupIdAndTermIdAndDeletedAtIsNull(
            UUID schoolId, UUID classGroupId, UUID termId);

    List<Assessment> findBySchoolIdAndSubjectIdAndDeletedAtIsNull(
            UUID schoolId, UUID subjectId);

    List<Assessment> findBySchoolIdAndTeacherMembershipIdAndDeletedAtIsNull(
            UUID schoolId, UUID teacherMembershipId);

    List<Assessment> findBySchoolIdAndPublishedAndDeletedAtIsNull(
            UUID schoolId, Boolean published);
}
