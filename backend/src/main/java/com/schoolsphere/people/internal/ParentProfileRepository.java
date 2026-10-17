package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentProfileRepository extends JpaRepository<ParentProfile, UUID> {

    List<ParentProfile> findBySchoolIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID schoolId);

    List<ParentProfile> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, List<UUID> ids);

    Optional<ParentProfile> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<ParentProfile> findBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);

    boolean existsBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);
}
