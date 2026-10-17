package com.schoolsphere.notifications.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.common.util.Uuid7;
import com.schoolsphere.notifications.api.NotificationsApi;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
        Set<String> resolvedChannels = resolveChannels(channels);
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

    @Override
    public int scheduleNotifications(Collection<UUID> userIds, String type, String title, String body,
                                     Map<String, Object> data, Collection<String> channels, Instant dueAt,
                                     String dedupeKey) {
        UUID schoolId = TenantContext.schoolId();
        if (dueAt == null) {
            throw ApiException.badRequest("due_at_required", "A scheduled notification needs a due time.");
        }
        Set<String> resolvedChannels = resolveChannels(channels);
        Map<String, Object> payloadData = data == null ? Map.of() : data;
        String json;
        try {
            json = objectMapper.writeValueAsString(payloadData);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise notification data", e);
        }
        OffsetDateTime due = OffsetDateTime.ofInstant(dueAt, ZoneOffset.UTC);
        int created = 0;
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            for (String channel : resolvedChannels) {
                // The dedupe key carries the channel too, so one reminder can still be scheduled on
                // several channels while remaining idempotent per channel.
                created += jdbcTemplate.update("""
                        INSERT INTO notification (id, school_id, user_id, notification_type, title, body,
                                                  data, channel, state, due_at, dedupe_key)
                        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'pending', ?, ?)
                        ON CONFLICT (school_id, dedupe_key) WHERE dedupe_key IS NOT NULL DO NOTHING
                        """, Uuid7.generate(), schoolId, userId, type, title, body, json, channel, due,
                        dedupeKey + ":" + channel);
            }
        }
        return created;
    }

    @Override
    public int cancelScheduled(String dedupeKeyPrefix) {
        if (dedupeKeyPrefix == null || dedupeKeyPrefix.isBlank()) {
            throw ApiException.badRequest("dedupe_prefix_required", "A dedupe key prefix is required.");
        }
        return jdbcTemplate.update("DELETE FROM notification WHERE school_id = ? AND state = 'pending' "
                        + "AND due_at IS NOT NULL AND dedupe_key LIKE ? ESCAPE '\\'",
                TenantContext.schoolId(), escapeLike(dedupeKeyPrefix) + "%");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Set<String> resolveChannels(Collection<String> channels) {
        Set<String> resolved = new LinkedHashSet<>(
                channels == null || channels.isEmpty() ? List.of("in_app") : channels);
        for (String channel : resolved) {
            if (!VALID_CHANNELS.contains(channel)) {
                throw ApiException.badRequest("invalid_channel", "Unknown notification channel: " + channel);
            }
        }
        return resolved;
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
        PageRequest page = PageRequest.of(0, size);
        Instant now = Instant.now();
        List<Notification> rows = unreadOnly
                ? notificationRepository.findVisibleUnread(schoolId, userId, now, page)
                : notificationRepository.findVisible(schoolId, userId, now, page);
        return rows.stream().map(this::toView).toList();
    }

    public long unreadCount() {
        return notificationRepository.countVisibleUnread(TenantContext.schoolId(),
                TenantContext.actorUserId(), Instant.now());
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
