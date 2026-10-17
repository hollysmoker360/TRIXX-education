package com.schoolsphere.examinations.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One candidate's sitting of an assessment: registration, marks and moderation. */
@Entity
@Table(name = "exam_script")
public class ExamScript extends TenantEntity {
    UUID sessionId;
    UUID assessmentId;
    UUID studentId;
    String seatNumber;
    String status = "PENDING";
    BigDecimal marks;
    String remarks;
    UUID markedBy;
    Instant markedAt;
    UUID moderatedBy;
    Instant moderatedAt;

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID v) { this.sessionId = v; }
    public UUID getAssessmentId() { return assessmentId; }
    public void setAssessmentId(UUID v) { this.assessmentId = v; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID v) { this.studentId = v; }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String v) { this.seatNumber = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getMarks() { return marks; }
    public void setMarks(BigDecimal v) { this.marks = v; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String v) { this.remarks = v; }
    public UUID getMarkedBy() { return markedBy; }
    public void setMarkedBy(UUID v) { this.markedBy = v; }
    public Instant getMarkedAt() { return markedAt; }
    public void setMarkedAt(Instant v) { this.markedAt = v; }
    public UUID getModeratedBy() { return moderatedBy; }
    public void setModeratedBy(UUID v) { this.moderatedBy = v; }
    public Instant getModeratedAt() { return moderatedAt; }
    public void setModeratedAt(Instant v) { this.moderatedAt = v; }
}
