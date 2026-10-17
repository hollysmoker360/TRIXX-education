package com.schoolsphere.notifications.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findBySchoolIdAndId(UUID schoolId, UUID id);

    List<Notification> findBySchoolIdAndUserIdOrderByCreatedAtDesc(UUID schoolId, UUID userId, Pageable pageable);

    List<Notification> findBySchoolIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID schoolId, UUID userId,
                                                                                 Pageable pageable);

    List<Notification> findBySchoolIdAndUserIdAndReadAtIsNull(UUID schoolId, UUID userId);

    long countBySchoolIdAndUserIdAndReadAtIsNull(UUID schoolId, UUID userId);

    /**
     * Inbox page (spec 11.7): scheduled notifications are invisible until they are due, so a lesson
     * reminder written two minutes before the lesson cannot leak into the list early.
     */
    @Query("SELECT n FROM Notification n WHERE n.schoolId = :schoolId AND n.userId = :userId "
            + "AND (n.dueAt IS NULL OR n.dueAt <= :now) ORDER BY n.createdAt DESC")
    List<Notification> findVisible(@Param("schoolId") UUID schoolId, @Param("userId") UUID userId,
                                   @Param("now") Instant now, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.schoolId = :schoolId AND n.userId = :userId "
            + "AND n.readAt IS NULL AND (n.dueAt IS NULL OR n.dueAt <= :now) ORDER BY n.createdAt DESC")
    List<Notification> findVisibleUnread(@Param("schoolId") UUID schoolId, @Param("userId") UUID userId,
                                         @Param("now") Instant now, Pageable pageable);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.schoolId = :schoolId AND n.userId = :userId "
            + "AND n.readAt IS NULL AND (n.dueAt IS NULL OR n.dueAt <= :now)")
    long countVisibleUnread(@Param("schoolId") UUID schoolId, @Param("userId") UUID userId,
                            @Param("now") Instant now);
}
