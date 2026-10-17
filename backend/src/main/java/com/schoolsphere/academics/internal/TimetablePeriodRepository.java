package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetablePeriodRepository extends JpaRepository<TimetablePeriod, UUID> {

    List<TimetablePeriod> findBySchoolIdAndDeletedAtIsNullOrderBySeq(UUID schoolId);

    List<TimetablePeriod> findBySchoolIdAndAcademicYearIdAndDeletedAtIsNullOrderBySeq(UUID schoolId,
                                                                                       UUID academicYearId);

    Optional<TimetablePeriod> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);
}
