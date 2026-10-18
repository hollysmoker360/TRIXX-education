package com.schoolsphere.grades.internal.repository;

import com.schoolsphere.grades.internal.entity.GradeEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeEntryRepository extends JpaRepository<GradeEntry, UUID> {

    Optional<GradeEntry> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<GradeEntry> findBySchoolIdAndAssessmentIdAndStudentIdAndDeletedAtIsNull(
            UUID schoolId, UUID assessmentId, UUID studentId);

    List<GradeEntry> findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
            UUID schoolId, UUID assessmentId);

    List<GradeEntry> findBySchoolIdAndStudentIdAndDeletedAtIsNull(
            UUID schoolId, UUID studentId);

    List<GradeEntry> findBySchoolIdAndAssessmentIdAndStatusAndDeletedAtIsNull(
            UUID schoolId, UUID assessmentId, String status);
}
