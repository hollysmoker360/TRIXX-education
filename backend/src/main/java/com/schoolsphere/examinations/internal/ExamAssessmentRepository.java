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

public interface ExamAssessmentRepository extends JpaRepository<ExamAssessment, UUID> {

    @Query("select a from ExamAssessment a where a.schoolId = :schoolId and a.deletedAt is null"
            + " and (:status is null or a.status = :status) order by a.createdAt desc")
    Page<ExamAssessment> search(@Param("schoolId") UUID schoolId, @Param("status") String status, Pageable pageable);

    Optional<ExamAssessment> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<ExamAssessment> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> ids);
}
