package com.schoolsphere.lessons.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Live lesson conferencing behind a provider seam (spec 7.5): the default implementation targets
 * Jitsi Meet, and self-hosted deployments can supply their own bean.
 */
public interface ConferencingProvider {

    Room room(UUID schoolId, UUID classGroupId, UUID subjectId, String topic, LocalDate date);

    record Room(String room, String url, String topic) {
    }
}
