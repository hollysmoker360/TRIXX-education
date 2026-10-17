package com.schoolsphere.examinations.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_session")
public class ExamSession extends TenantEntity {
    UUID assessmentId;
    String title;
    Instant startsAt;
    Instant endsAt;
    String venue;
    String status = "SCHEDULED";

    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID v) { this.assessmentId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant v) { this.startsAt = v; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant v) { this.endsAt = v; }
    public String getVenue() { return venue; }
    public void setVenue(String v) { this.venue = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
}
