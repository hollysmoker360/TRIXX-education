package com.schoolsphere.academics.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermRepository extends JpaRepository<Term, UUID> {

    List<Term> findBySchoolIdAndAcademicYearIdAndDeletedAtIsNullOrderBySeq(UUID schoolId, UUID academicYearId);

    List<Term> findBySchoolIdAndDeletedAtIsNullOrderByStartsOnDesc(UUID schoolId);

    Optional<Term> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<Term> findBySchoolIdAndIsCurrentTrueAndDeletedAtIsNull(UUID schoolId);

    boolean existsBySchoolIdAndIsCurrentTrueAndDeletedAtIsNull(UUID schoolId);
}
