package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    @Query("""
            select a from Assignment a
            where a.schoolId = :schoolId and a.deletedAt is null
              and (:classGroupId is null or a.classGroupId = :classGroupId)
              and (:termId is null or a.termId = :termId)
              and (:state is null or a.state = :state)
            order by a.createdAt desc
            """)
    Page<Assignment> search(UUID schoolId, UUID classGroupId, UUID termId, String state, Pageable pageable);

    Optional<Assignment> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Assignment> findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(UUID schoolId, UUID classGroupId);

    List<Assignment> findBySchoolIdAndTeacherMembershipIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId,
                                                                                                 UUID teacherMembershipId);

    List<Assignment> findBySchoolIdAndStateAndDeletedAtIsNullOrderByDueAtAsc(UUID schoolId, String state);
}
