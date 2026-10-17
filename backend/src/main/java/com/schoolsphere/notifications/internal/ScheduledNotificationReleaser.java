package com.schoolsphere.notifications.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.tenant.TenantScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Releases scheduled notifications when they fall due (spec 12.2). Claiming clears {@code due_at}
 * atomically with {@code FOR UPDATE SKIP LOCKED}, so a row is handed to exactly one worker even with
 * several instances; in-app rows are simply marked sent, while external rows are enqueued on the
 * transactional outbox and then finish through the normal delivery path, so a reminder inherits the
 * outbox's retry and de-duplication guarantees.
 */
@Service
@TenantScoped
public class ScheduledNotificationReleaser {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNotificationReleaser.class);
    private static final int BATCH_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final UserDirectory userDirectory;

    public ScheduledNotificationReleaser(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                                         UserDirectory userDirectory) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.userDirectory = userDirectory;
    }

    /** Releases the notifications due for the current tenant. Returns how many rows were claimed. */
    public int releaseDue() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                UPDATE notification SET due_at = NULL
                WHERE id IN (
                    SELECT id FROM notification
                    WHERE state = 'pending' AND due_at IS NOT NULL AND due_at <= now()
                    ORDER BY due_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, school_id, user_id, channel, title, body, data::text AS data
                """, BATCH_SIZE);
        for (Map<String, Object> row : rows) {
            release(row);
        }
        return rows.size();
    }

    private void release(Map<String, Object> row) {
        UUID id = (UUID) row.get("id");
        String channel = String.valueOf(row.get("channel"));
        if ("in_app".equals(channel)) {
            jdbcTemplate.update("UPDATE notification SET state = 'sent', sent_at = now() WHERE id = ?", id);
            return;
        }
        try {
            enqueueExternal(row);
        } catch (Exception e) {
            log.warn("notification.release.failed id={} error={}", id, e.getMessage());
            jdbcTemplate.update("UPDATE notification SET state = 'failed' WHERE id = ?", id);
        }
    }

    private void enqueueExternal(Map<String, Object> row) throws Exception {
        UUID schoolId = (UUID) row.get("school_id");
        UUID userId = (UUID) row.get("user_id");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", String.valueOf(row.get("id")));
        payload.put("schoolId", schoolId.toString());
        payload.put("userId", userId.toString());
        payload.put("channel", String.valueOf(row.get("channel")));
        payload.put("title", row.get("title"));
        payload.put("body", row.get("body") == null ? "" : row.get("body"));
        payload.put("data", objectMapper.readValue(String.valueOf(row.get("data")),
                new TypeReference<Map<String, Object>>() {
                }));
        userDirectory.findById(userId).ifPresent(user -> {
            payload.put("email", user.email());
            payload.put("phone", user.phone());
        });
        jdbcTemplate.update("INSERT INTO notification_outbox (school_id, topic, payload) VALUES (?, ?, ?::jsonb)",
                schoolId, "notification.dispatch", objectMapper.writeValueAsString(payload));
    }
}
