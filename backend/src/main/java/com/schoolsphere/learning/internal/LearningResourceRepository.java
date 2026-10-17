package com.schoolsphere.learning.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LearningResourceRepository extends JpaRepository<LearningResource, UUID> {

    @Query("""
            select r from LearningResource r
            where r.schoolId = :schoolId and r.deletedAt is null
              and (:classGroupId is null or r.classGroupId = :classGroupId)
              and (:subjectId is null or r.subjectId = :subjectId)
              and (:state is null or r.state = :state)
            order by r.createdAt desc
            """)
    Page<LearningResource> search(UUID schoolId, UUID classGroupId, UUID subjectId, String state, Pageable pageable);

    Page<LearningResource> findBySchoolIdAndDeletedAtIsNull(UUID schoolId, Pageable pageable);

    Optional<LearningResource> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<LearningResource> findBySchoolIdAndStateAndDeletedAtIsNullOrderByPublishAtDesc(UUID schoolId, String state);

    List<LearningResource> findBySchoolIdAndStateAndPublishAtLessThanEqualAndDeletedAtIsNull(UUID schoolId,
                                                                                            String state, Instant at);
}
