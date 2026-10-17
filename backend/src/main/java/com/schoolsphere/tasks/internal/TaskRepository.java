package com.schoolsphere.tasks.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findBySchoolIdAndOwnerUserIdAndDeletedAtIsNullOrderByDueAtAsc(UUID schoolId, UUID ownerUserId);

    Optional<Task> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<Task> findBySchoolIdAndOwnerUserIdAndTaskKeyAndDeletedAtIsNull(UUID schoolId, UUID ownerUserId,
                                                                            String taskKey);

    long countBySchoolIdAndOwnerUserIdAndDoneAtIsNullAndDeletedAtIsNull(UUID schoolId, UUID ownerUserId);
}
