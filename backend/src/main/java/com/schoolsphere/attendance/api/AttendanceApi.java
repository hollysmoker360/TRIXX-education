package com.schoolsphere.attendance.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public API of the attendance module (spec 4.4, 11.6). Events are append-only; the sync engine
 * applies offline captures through {@link #applyEvent}. The interface is open so future sources
 * (QR, biometric, camera) can submit events through the same authorization and audit path.
 */
public interface AttendanceApi {

    EventOutcome applyEvent(EventInput input);

    record EventInput(
            UUID id,
            UUID sessionId,
            UUID studentId,
            String status,
            String reason,
            Instant eventTime,
            String originDeviceId,
            UUID correctsEventId) {
    }

    record EventOutcome(EventView event, boolean created) {
    }

    record EventView(
            UUID id,
            UUID sessionId,
            UUID studentId,
            String status,
            String reason,
            Instant eventTime,
            Instant receivedAt,
            UUID correctsEventId) {
    }
}
