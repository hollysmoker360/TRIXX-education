package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.SupportGoal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportGoalRepository extends JpaRepository<SupportGoal, UUID> {

    List<SupportGoal> findBySchoolIdAndPlanIdAndDeletedAtIsNull(UUID schoolId, UUID planId);
}
