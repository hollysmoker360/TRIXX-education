package com.schoolsphere.featureflags.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, UUID> {

    List<FeatureFlagEntity> findByFlagKey(String flagKey);

    Optional<FeatureFlagEntity> findByFlagKeyAndSchoolId(String flagKey, UUID schoolId);

    Optional<FeatureFlagEntity> findByFlagKeyAndSchoolIdIsNull(String flagKey);
}
