package com.schoolsphere.notifications.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.common.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Transactional outbox worker (spec 3.1, 11.7): claims pending rows with FOR UPDATE SKIP LOCKED
 * so multiple instances cannot double-send, delivers external channels behind provider
 * interfaces, and retries with exponential backoff. A claim acts as a five-minute lease so rows
 * are reclaimed if a worker crashes mid-delivery.
 */
@Component
public class OutboxWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ATTEMPTS = 8;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Providers.EmailProvider emailProvider;
    private final Providers.SmsProvider smsProvider;
    private final NotificationStateService notificationStateService;

    public OutboxWorker(JdbcTemplate jdbcTemplate,
                        ObjectMapper objectMapper,
                        Providers.EmailProvider emailProvider,
                        Providers.SmsProvider smsProvider,
                        NotificationStateService notificationStateService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.emailProvider = emailProvider;
        this.smsProvider = smsProvider;
        this.notificationStateService = notificationStateService;
    }

    @Scheduled(fixedDelayString = "${schoolsphere.notifications.outbox-delay-ms:3000}")
    public void poll() {
        reapStaleClaims();
        List<Map<String, Object>> rows = claimBatch();
        for (Map<String, Object> row : rows) {
            processRow(row);
        }
    }

    private void reapStaleClaims() {
        jdbcTemplate.update("""
                UPDATE notification_outbox SET status = 'pending'
                WHERE status = 'processing' AND next_attempt_at <= now()
                """);
    }

    private List<Map<String, Object>> claimBatch() {
        return jdbcTemplate.queryForList("""
                UPDATE notification_outbox
                SET status = 'processing', attempts = attempts + 1, next_attempt_at = now() + interval '5 minutes'
                WHERE id IN (
                    SELECT id FROM notification_outbox
                    WHERE status = 'pending' AND next_attempt_at <= now()
                    ORDER BY created_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, school_id, topic, payload::text AS payload, attempts
                """, BATCH_SIZE);
    }

    private void processRow(Map<String, Object> row) {
        UUID id = (UUID) row.get("id");
        UUID schoolId = (UUID) row.get("school_id");
        String topic = (String) row.get("topic");
        int attempts = ((Number) row.get("attempts")).intValue();
        try {
            Map<String, Object> payload = objectMapper.readValue((String) row.get("payload"),
                    new TypeReference<>() {
                    });
            if ("notification.dispatch".equals(topic)) {
                deliver(schoolId, payload);
            }
            complete(id);
        } catch (Exception e) {
            failOrRetry(id, attempts, e);
        }
    }

    private void deliver(UUID schoolId, Map<String, Object> payload) {
        String channel = String.valueOf(payload.get("channel"));
        String title = String.valueOf(payload.get("title"));
        String body = payload.get("body") == null ? "" : String.valueOf(payload.get("body"));
        UUID notificationId = UUID.fromString(String.valueOf(payload.get("notificationId")));
        switch (channel) {
            case "email" -> {
                String email = stringOrNull(payload.get("email"));
                if (email == null) {
                    markFailed(schoolId, notificationId, "no_email_address");
                    return;
                }
                emailProvider.send(email, title, body);
                markSent(schoolId, notificationId);
            }
            case "sms" -> {
                String phone = stringOrNull(payload.get("phone"));
                if (phone == null) {
                    markFailed(schoolId, notificationId, "no_phone_number");
                    return;
                }
                smsProvider.send(phone, title + ": " + body);
                markSent(schoolId, notificationId);
            }
            default -> markFailed(schoolId, notificationId, "channel_not_configured");
        }
    }

    private void markSent(UUID schoolId, UUID notificationId) {
        TenantContext.run(schoolId, null, null, () -> {
            notificationStateService.markSent(notificationId);
            return null;
        });
    }

    private void markFailed(UUID schoolId, UUID notificationId, String reason) {
        TenantContext.run(schoolId, null, null, () -> {
            notificationStateService.markFailed(notificationId, reason);
            return null;
        });
    }

    private void complete(UUID id) {
        jdbcTemplate.update(
                "UPDATE notification_outbox SET status = 'done', processed_at = now(), last_error = NULL WHERE id = ?",
                id);
    }

    private void failOrRetry(UUID id, int attempts, Exception e) {
        String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (error.length() > 500) {
            error = error.substring(0, 500);
        }
        if (attempts >= MAX_ATTEMPTS) {
            log.warn("outbox.dispatch.failed id={} attempts={} error={}", id, attempts, error);
            jdbcTemplate.update("""
                    UPDATE notification_outbox SET status = 'failed', last_error = ? WHERE id = ?
                    """, error, id);
            return;
        }
        long backoffSeconds = Math.min(1800, 30L * (1L << Math.min(attempts, 6)));
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = 'pending', last_error = ?, next_attempt_at = now() + make_interval(secs => ?)
                WHERE id = ?
                """, error, (double) backoffSeconds, id);
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }
}
