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

public interface ExamScriptRepository extends JpaRepository<ExamScript, UUID> {

    Optional<ExamScript> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<ExamScript> findBySchoolIdAndSessionIdAndDeletedAtIsNull(UUID schoolId, UUID sessionId);

    List<ExamScript> findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(UUID schoolId, UUID assessmentId);

    List<ExamScript> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);

    List<ExamScript> findBySchoolIdAndAssessmentIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> assessmentIds);

    long countBySchoolIdAndSessionIdAndDeletedAtIsNull(UUID schoolId, UUID sessionId);

    /** Scripts of completed sittings whose results are not yet published. */
    @Query("select s from ExamScript s, ExamSession x, ExamAssessment a"
            + " where x.id = s.sessionId and a.id = s.assessmentId and s.schoolId = :schoolId"
            + " and s.deletedAt is null and x.status = 'COMPLETED' and a.resultsPublishedAt is null"
            + " and s.status in :statuses order by s.createdAt asc")
    Page<ExamScript> markingQueue(@Param("schoolId") UUID schoolId, @Param("statuses") Collection<String> statuses,
                                  Pageable pageable);

    @Query("select distinct s.assessmentId from ExamScript s where s.schoolId = :schoolId and s.deletedAt is null")
    List<UUID> assessmentsWithCandidates(@Param("schoolId") UUID schoolId);
}
