package com.schoolsphere.notifications.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findBySchoolIdAndId(UUID schoolId, UUID id);

    List<Notification> findBySchoolIdAndUserIdOrderByCreatedAtDesc(UUID schoolId, UUID userId, Pageable pageable);

    List<Notification> findBySchoolIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID schoolId, UUID userId,
                                                                                 Pageable pageable);

    List<Notification> findBySchoolIdAndUserIdAndReadAtIsNull(UUID schoolId, UUID userId);

    long countBySchoolIdAndUserIdAndReadAtIsNull(UUID schoolId, UUID userId);
}
