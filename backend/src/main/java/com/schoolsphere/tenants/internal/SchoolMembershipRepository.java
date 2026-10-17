package com.schoolsphere.tenants.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolMembershipRepository extends JpaRepository<SchoolMembership, UUID> {

    Optional<SchoolMembership> findBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);

    List<SchoolMembership> findByUserIdAndDeletedAtIsNull(UUID userId);

    List<SchoolMembership> findBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    Optional<SchoolMembership> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
