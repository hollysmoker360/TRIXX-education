package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceCompletionRepository extends JpaRepository<ResourceCompletion, UUID> {

    Optional<ResourceCompletion> findBySchoolIdAndResourceIdAndStudentId(UUID schoolId, UUID resourceId,
                                                                         UUID studentId);

    List<ResourceCompletion> findBySchoolIdAndStudentId(UUID schoolId, UUID studentId);
}
