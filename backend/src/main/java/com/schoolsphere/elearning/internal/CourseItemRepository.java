package com.schoolsphere.elearning.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseItemRepository extends JpaRepository<CourseItem, UUID> {

    List<CourseItem> findBySchoolIdAndCourseIdAndDeletedAtIsNullOrderByPositionAsc(UUID schoolId, UUID courseId);

    List<CourseItem> findBySchoolIdAndCourseIdInAndDeletedAtIsNull(UUID schoolId, Collection<UUID> courseIds);

    Optional<CourseItem> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    boolean existsBySchoolIdAndCourseIdAndItemTypeAndRefIdAndDeletedAtIsNull(UUID schoolId, UUID courseId,
                                                                             String itemType, UUID refId);
}
