package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findBySchoolIdAndDeletedAtIsNullOrderByName(UUID schoolId);

    Optional<Subject> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Subject> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, List<UUID> ids);

    boolean existsBySchoolIdAndNameAndDeletedAtIsNull(UUID schoolId, String name);
}
