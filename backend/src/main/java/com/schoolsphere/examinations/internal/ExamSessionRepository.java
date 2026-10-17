package com.schoolsphere.examinations.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {

    @Query("select s from ExamSession s where s.schoolId = :schoolId and s.deletedAt is null"
            + " and (:assessmentId is null or s.assessmentId = :assessmentId) order by s.startsAt desc")
    Page<ExamSession> search(@Param("schoolId") UUID schoolId, @Param("assessmentId") UUID assessmentId,
                             Pageable pageable);

    Optional<ExamSession> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<ExamSession> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> ids);

    boolean existsBySchoolIdAndAssessmentIdAndDeletedAtIsNull(UUID schoolId, UUID assessmentId);
}
