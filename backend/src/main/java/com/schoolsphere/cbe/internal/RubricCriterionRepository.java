package com.schoolsphere.cbe.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RubricCriterionRepository extends JpaRepository<RubricCriterion, UUID> {
    List<RubricCriterion> findBySchoolIdAndRubricIdAndDeletedAtIsNull(UUID schoolId, UUID rubricId);
}
