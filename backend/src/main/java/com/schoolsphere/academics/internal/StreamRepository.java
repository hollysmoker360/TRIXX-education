package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreamRepository extends JpaRepository<Stream, UUID> {

    List<Stream> findBySchoolIdAndClassGroupIdAndDeletedAtIsNullOrderByName(UUID schoolId, UUID classGroupId);

    List<Stream> findBySchoolIdAndDeletedAtIsNull(UUID schoolId);

    Optional<Stream> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
