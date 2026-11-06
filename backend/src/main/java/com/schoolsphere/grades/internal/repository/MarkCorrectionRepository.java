package com.schoolsphere.grades.internal.repository;

import com.schoolsphere.grades.internal.entity.MarkCorrection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarkCorrectionRepository extends JpaRepository<MarkCorrection, UUID> {

    Optional<MarkCorrection> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<MarkCorrection> findBySchoolIdAndGradeEntryIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID schoolId, UUID gradeEntryId);
}
