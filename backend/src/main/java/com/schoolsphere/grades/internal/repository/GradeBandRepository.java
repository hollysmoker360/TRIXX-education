package com.schoolsphere.grades.internal.repository;

import com.schoolsphere.grades.internal.entity.GradeBand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeBandRepository extends JpaRepository<GradeBand, UUID> {

    List<GradeBand> findBySchoolIdAndDeletedAtIsNullOrderByMinScoreAsc(UUID schoolId);
}
