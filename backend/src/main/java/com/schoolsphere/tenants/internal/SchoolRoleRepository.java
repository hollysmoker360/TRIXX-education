package com.schoolsphere.tenants.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRoleRepository extends JpaRepository<SchoolRole, UUID> {

    Optional<SchoolRole> findBySchoolIdAndCodeAndDeletedAtIsNull(UUID schoolId, String code);

    List<SchoolRole> findBySchoolIdAndDeletedAtIsNull(UUID schoolId);
}
