package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    List<AcademicYear> findBySchoolIdAndDeletedAtIsNullOrderByStartsOnDesc(UUID schoolId);

    Optional<AcademicYear> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<AcademicYear> findBySchoolIdAndIsActiveTrueAndDeletedAtIsNull(UUID schoolId);

    boolean existsBySchoolIdAndNameAndDeletedAtIsNull(UUID schoolId, String name);
}
