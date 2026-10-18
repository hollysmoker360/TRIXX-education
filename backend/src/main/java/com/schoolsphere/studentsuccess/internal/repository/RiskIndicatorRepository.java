package com.schoolsphere.studentsuccess.internal.repository;

import com.schoolsphere.studentsuccess.internal.entity.RiskIndicator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskIndicatorRepository extends JpaRepository<RiskIndicator, UUID> {

    Optional<RiskIndicator> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<RiskIndicator> findBySchoolIdAndStudentIdAndDeletedAtIsNull(
            UUID schoolId, UUID studentId);

    List<RiskIndicator> findBySchoolIdAndStudentIdAndAcknowledgedAndDeletedAtIsNull(
            UUID schoolId, UUID studentId, Boolean acknowledged);

    List<RiskIndicator> findBySchoolIdAndAcknowledgedAndResolvedAndDeletedAtIsNull(
            UUID schoolId, Boolean acknowledged, Boolean resolved);
}
