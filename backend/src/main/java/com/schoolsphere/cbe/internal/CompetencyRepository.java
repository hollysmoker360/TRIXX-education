package com.schoolsphere.cbe.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetencyRepository extends JpaRepository<Competency, UUID> {
    List<Competency> findBySchoolIdAndCurriculumVersionIdAndDeletedAtIsNull(UUID schoolId, UUID curriculumVersionId);
    Optional<Competency> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
