package com.schoolsphere.attendance.internal;

import com.schoolsphere.common.util.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only attendance event; V8 declares no update or soft-delete columns (spec 11.6). */
@Entity
@Table(name = "attendance_event")
public class AttendanceEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "school_id", nullable = false, updatable = false)
    UUID schoolId;

    UUID sessionId;
    UUID studentId;
    String status;
    String reason;
    Instant eventTime;
    Instant receivedAt;
    String originDeviceId;
    UUID correctsEventId;
    UUID recordedByMembershipId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPersist() {
        if (id == null) {
            id = Uuid7.generate();
        }
        Instant now = Instant.now();
        if (receivedAt == null) {
            receivedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (eventTime == null) {
            eventTime = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getOriginDeviceId() {
        return originDeviceId;
    }

    public void setOriginDeviceId(String originDeviceId) {
        this.originDeviceId = originDeviceId;
    }

    public UUID getCorrectsEventId() {
        return correctsEventId;
    }

    public void setCorrectsEventId(UUID correctsEventId) {
        this.correctsEventId = correctsEventId;
    }

    public UUID getRecordedByMembershipId() {
        return recordedByMembershipId;
    }

    public void setRecordedByMembershipId(UUID recordedByMembershipId) {
        this.recordedByMembershipId = recordedByMembershipId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
