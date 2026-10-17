package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceVersionRepository extends JpaRepository<ResourceVersion, UUID> {

    List<ResourceVersion> findByResourceIdOrderByVersionNoDesc(UUID resourceId);

    Optional<ResourceVersion> findByResourceIdAndVersionNo(UUID resourceId, int versionNo);

    Optional<ResourceVersion> findBySchoolIdAndId(UUID schoolId, UUID id);
}
