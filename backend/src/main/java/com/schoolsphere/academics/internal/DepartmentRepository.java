package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findBySchoolIdAndDeletedAtIsNullOrderByName(UUID schoolId);

    Optional<Department> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    boolean existsBySchoolIdAndNameAndDeletedAtIsNull(UUID schoolId, String name);
}
