package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.InterventionPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterventionPlanRepository extends JpaRepository<InterventionPlan, UUID> {

    Optional<InterventionPlan> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<InterventionPlan> findBySchoolIdAndStudentIdAndDeletedAtIsNull(
            UUID schoolId, UUID studentId);

    List<InterventionPlan> findBySchoolIdAndReviewDateAndStatusAndDeletedAtIsNull(
            UUID schoolId, java.time.LocalDate reviewDate, String status);
}
