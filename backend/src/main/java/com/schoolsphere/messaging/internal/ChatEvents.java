package com.schoolsphere.messaging.internal;

import com.schoolsphere.messaging.api.MessagingApi;
import java.util.UUID;

/** In-process broadcast events relayed to STOMP topics after commit. */
public final class ChatEvents {

    private ChatEvents() {
    }

    public record MessageChanged(UUID schoolId, UUID channelId, MessagingApi.MessageView message) {
    }

    public record Typing(UUID schoolId, UUID channelId, UUID membershipId, String displayName) {
    }

    public static String topic(UUID schoolId, UUID channelId) {
        return "/topic/school." + schoolId + ".channel." + channelId;
    }
}
