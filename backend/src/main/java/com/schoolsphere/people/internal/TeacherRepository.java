package com.schoolsphere.people.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    List<Teacher> findBySchoolIdAndDeletedAtIsNullOrderByLastNameAscFirstNameAsc(UUID schoolId);

    List<Teacher> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, List<UUID> ids);

    Optional<Teacher> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<Teacher> findBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);

    boolean existsBySchoolIdAndUserIdAndDeletedAtIsNull(UUID schoolId, UUID userId);
}
