package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, UUID> {

    List<ClassGroup> findBySchoolIdAndDeletedAtIsNullOrderByLevelAscNameAsc(UUID schoolId);

    Optional<ClassGroup> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<ClassGroup> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, List<UUID> ids);

    boolean existsBySchoolIdAndNameAndDeletedAtIsNull(UUID schoolId, String name);
}
