package com.schoolsphere.notifications.api;

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
}
