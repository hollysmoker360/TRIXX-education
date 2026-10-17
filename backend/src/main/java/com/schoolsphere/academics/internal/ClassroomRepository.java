package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomRepository extends JpaRepository<Classroom, UUID> {

    List<Classroom> findBySchoolIdAndDeletedAtIsNullOrderByName(UUID schoolId);

    Optional<Classroom> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
