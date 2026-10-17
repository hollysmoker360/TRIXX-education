package com.schoolsphere.messaging.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public API of the messaging module (spec 4.4). Used by the sync engine to apply offline
 * message creates idempotently (spec 10.4: append-only, dedupe on client-generated id).
 */
public interface MessagingApi {

    /** Idempotent append: dedupes on (channel, clientId) and on an existing id. */
    SendOutcome sendMessage(MessageInput input);

    record MessageInput(
            UUID id,
            UUID clientId,
            UUID channelId,
            String body,
            UUID replyToId,
            List<Map<String, Object>> attachments) {
    }

    record SendOutcome(MessageView message, boolean created) {
    }

    record MessageView(
            UUID id,
            UUID channelId,
            UUID clientId,
            UUID senderMembershipId,
            String senderName,
            String body,
            UUID replyToId,
            List<Map<String, Object>> attachments,
            Long seq,
            Instant sentAt,
            Instant editedAt,
            Instant deletedAt) {
    }
}
