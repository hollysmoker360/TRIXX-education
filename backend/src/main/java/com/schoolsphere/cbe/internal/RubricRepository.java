package com.schoolsphere.cbe.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricRepository extends JpaRepository<Rubric, UUID> {
    List<Rubric> findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId);
    Optional<Rubric> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
