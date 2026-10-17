package com.schoolsphere.lessons.internal;

import com.schoolsphere.lessons.api.ConferencingProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Jitsi Meet provider. Room names are deterministic (school + class + subject + day) so every
 * participant computes the same room without server-side state; links are the room-named URL.
 */
@Component
public class JitsiConferencingProvider implements ConferencingProvider {

    private final String baseUrl;
    private final String roomPrefix;

    public JitsiConferencingProvider(
            @Value("${schoolsphere.conferencing.base-url:https://meet.jit.si}") String baseUrl,
            @Value("${schoolsphere.conferencing.room-prefix:schoolsphere}") String roomPrefix) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.roomPrefix = roomPrefix;
    }

    @Override
    public Room room(UUID schoolId, UUID classGroupId, UUID subjectId, String topic, LocalDate date) {
        String scope = subjectId == null ? "class" : "subject-" + subjectId;
        String material = schoolId + ":" + classGroupId + ":" + scope + ":"
                + (topic == null || topic.isBlank() ? date.toString() : topic.trim().toLowerCase());
        String room = roomPrefix + "-" + shortHash(material);
        return new Room(room, baseUrl + "/" + room, topic);
    }

    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
