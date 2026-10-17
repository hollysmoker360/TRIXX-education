package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceBookmarkRepository extends JpaRepository<ResourceBookmark, UUID> {

    Optional<ResourceBookmark> findBySchoolIdAndResourceIdAndStudentId(UUID schoolId, UUID resourceId, UUID studentId);

    List<ResourceBookmark> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
}
