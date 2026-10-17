package com.schoolsphere.notifications.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Public API of the notifications module (spec 4.4, 11.7). Notifications are created through the
 * outbox so they survive crashes and are never duplicated.
 */
public interface NotificationsApi {

    /**
     * Creates notifications for the given users. In-app rows are readable immediately; external
     * channels (email, sms, push) are queued through the transactional outbox and delivered by a
     * worker. Must be called inside an active tenant-scoped transaction.
     */
    void notifyUsers(Collection<UUID> userIds, String type, String title, String body,
                     Map<String, Object> data, Collection<String> channels);

    /**
     * Schedules notifications for the given users to be released at {@code dueAt} (spec 12.2). Until
     * then the rows are hidden from the inbox; a worker releases them when they are due. The call is
     * idempotent per (school, user, {@code dedupeKey}, channel): re-scheduling the same reminder
     * inserts nothing the second time, which is what stops a lesson reminder being sent twice.
     *
     * @param dedupeKey idempotency key built from the occurrence, the recipient and the lead-time
     *                  offset; the channel is appended internally
     * @return the number of notifications actually created
     */
    int scheduleNotifications(Collection<UUID> userIds, String type, String title, String body,
                              Map<String, Object> data, Collection<String> channels, Instant dueAt,
                              String dedupeKey);

    /**
     * Cancels scheduled notifications whose dedupe key starts with {@code dedupeKeyPrefix} and that
     * have not been released yet (spec 12.2). Called when a published timetable is replaced, so a
     * reminder for a lesson that has moved is never delivered.
     *
     * @return the number of notifications cancelled
     */
    int cancelScheduled(String dedupeKeyPrefix);
}
