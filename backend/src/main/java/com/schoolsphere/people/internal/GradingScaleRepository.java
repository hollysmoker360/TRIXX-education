package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    List<GradingScale> findBySchoolIdAndDeletedAtIsNullOrderByNameAsc(UUID schoolId);

    Optional<GradingScale> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<GradingScale> findFirstBySchoolIdAndIsDefaultTrueAndDeletedAtIsNull(UUID schoolId);

    boolean existsBySchoolIdAndNameAndDeletedAtIsNull(UUID schoolId, String name);
}
