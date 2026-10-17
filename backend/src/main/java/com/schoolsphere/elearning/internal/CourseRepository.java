package com.schoolsphere.elearning.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    @Query("select c from Course c where c.schoolId = :schoolId and c.deletedAt is null"
            + " and (:status is null or c.status = :status) order by c.createdAt desc")
    Page<Course> search(@Param("schoolId") UUID schoolId, @Param("status") String status, Pageable pageable);

    Optional<Course> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Course> findBySchoolIdAndIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> ids);
}
