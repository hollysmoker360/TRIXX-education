package com.schoolsphere.cbe.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurriculumVersionRepository extends JpaRepository<CurriculumVersion, UUID> {
    List<CurriculumVersion> findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId);
    Optional<CurriculumVersion> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
