package com.schoolsphere.grades.internal.repository;

import com.schoolsphere.grades.internal.entity.AssessmentComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentComponentRepository extends JpaRepository<AssessmentComponent, UUID> {

    List<AssessmentComponent> findBySchoolIdAndAssessmentIdAndDeletedAtIsNullOrderByPositionAsc(
            UUID schoolId, UUID assessmentId);
}
