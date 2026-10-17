package com.schoolsphere.notifications.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.notifications.api.NotificationsApi;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Notification delivery (spec 11.7). In-app rows are readable immediately; external channels are
 * written to the transactional outbox in the same transaction (spec 3.1) and delivered by
 * {@link OutboxWorker}.
 */
@Service
@TenantScoped
public class NotificationService implements NotificationsApi {

    private static final Set<String> VALID_CHANNELS = Set.of("in_app", "email", "sms", "push");

    private final NotificationRepository notificationRepository;
    private final UserDirectory userDirectory;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               UserDirectory userDirectory,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.userDirectory = userDirectory;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyUsers(Collection<UUID> userIds, String type, String title, String body,
                            Map<String, Object> data, Collection<String> channels) {
        UUID schoolId = TenantContext.schoolId();
        Set<String> resolvedChannels = new LinkedHashSet<>(
                channels == null || channels.isEmpty() ? List.of("in_app") : channels);
        for (String channel : resolvedChannels) {
            if (!VALID_CHANNELS.contains(channel)) {
                throw ApiException.badRequest("invalid_channel", "Unknown notification channel: " + channel);
            }
        }
        Map<String, Object> payloadData = data == null ? Map.of() : data;
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            for (String channel : resolvedChannels) {
                Notification notification = new Notification();
                notification.setSchoolId(schoolId);
                notification.setUserId(userId);
                notification.setNotificationType(type);
                notification.setTitle(title);
                notification.setBody(body);
                notification.setData(new LinkedHashMap<>(payloadData));
                notification.setChannel(channel);
                if ("in_app".equals(channel)) {
                    notification.setState("sent");
                    notification.setSentAt(Instant.now());
                }
                notification = notificationRepository.save(notification);
                if (!"in_app".equals(channel)) {
                    enqueueExternal(notification, channel);
                }
            }
        }
    }

    private void enqueueExternal(Notification notification, String channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notification.getId().toString());
        payload.put("schoolId", notification.getSchoolId().toString());
        payload.put("userId", notification.getUserId().toString());
        payload.put("channel", channel);
        payload.put("title", notification.getTitle());
        payload.put("body", notification.getBody());
        payload.put("data", notification.getData());
        userDirectory.findById(notification.getUserId()).ifPresent(user -> {
            payload.put("email", user.email());
            payload.put("phone", user.phone());
        });
        try {
            jdbcTemplate.update(
                    "INSERT INTO notification_outbox (school_id, topic, payload) VALUES (?, ?, ?::jsonb)",
                    notification.getSchoolId(), "notification.dispatch",
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to enqueue outbox notification", e);
        }
    }

    public List<NotificationView> list(boolean unreadOnly, int limit) {
        UUID schoolId = TenantContext.schoolId();
        UUID userId = TenantContext.actorUserId();
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        List<Notification> rows = unreadOnly
                ? notificationRepository.findBySchoolIdAndUserIdAndReadAtIsNullOrderByCreatedAtDesc(
                        schoolId, userId, PageRequest.of(0, size))
                : notificationRepository.findBySchoolIdAndUserIdOrderByCreatedAtDesc(
                        schoolId, userId, PageRequest.of(0, size));
        return rows.stream().map(this::toView).toList();
    }

    public long unreadCount() {
        return notificationRepository.countBySchoolIdAndUserIdAndReadAtIsNull(TenantContext.schoolId(),
                TenantContext.actorUserId());
    }

    public NotificationView markRead(UUID notificationId) {
        Notification notification = notificationRepository
                .findBySchoolIdAndId(TenantContext.schoolId(), notificationId)
                .filter(n -> n.getUserId().equals(TenantContext.actorUserId()))
                .orElseThrow(() -> ApiException.notFound("notification_not_found", "Notification not found."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification.setState("read");
            notification = notificationRepository.save(notification);
        }
        return toView(notification);
    }

    public int markAllRead() {
        UUID schoolId = TenantContext.schoolId();
        UUID userId = TenantContext.actorUserId();
        List<Notification> unread = notificationRepository
                .findBySchoolIdAndUserIdAndReadAtIsNull(schoolId, userId);
        Instant now = Instant.now();
        for (Notification notification : unread) {
            notification.setReadAt(now);
            notification.setState("read");
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    private NotificationView toView(Notification n) {
        return new NotificationView(n.getId(), n.getNotificationType(), n.getTitle(), n.getBody(), n.getData(),
                n.getChannel(), n.getState(), n.getCreatedAt(), n.getReadAt());
    }

    public record NotificationView(UUID id, String type, String title, String body, Map<String, Object> data,
                                   String channel, String state, Instant createdAt, Instant readAt) {
    }
}
