package com.schoolsphere.grades.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "mark_correction")
public class MarkCorrection extends TenantEntity {

    @Column(name = "grade_entry_id", nullable = false)
    private UUID gradeEntryId;

    @Column(name = "previous_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal previousMarks;

    @Column(name = "new_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal newMarks;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "corrected_by", nullable = false)
    private UUID correctedBy;

    @Column(name = "corrected_at", nullable = false)
    private java.time.Instant correctedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private java.time.Instant approvedAt;

    public UUID getGradeEntryId() {
        return gradeEntryId;
    }

    public void setGradeEntryId(UUID gradeEntryId) {
        this.gradeEntryId = gradeEntryId;
    }

    public BigDecimal getPreviousMarks() {
        return previousMarks;
    }

    public void setPreviousMarks(BigDecimal previousMarks) {
        this.previousMarks = previousMarks;
    }

    public BigDecimal getNewMarks() {
        return newMarks;
    }

    public void setNewMarks(BigDecimal newMarks) {
        this.newMarks = newMarks;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getCorrectedBy() {
        return correctedBy;
    }

    public void setCorrectedBy(UUID correctedBy) {
        this.correctedBy = correctedBy;
    }

    public java.time.Instant getCorrectedAt() {
        return correctedAt;
    }

    public void setCorrectedAt(java.time.Instant correctedAt) {
        this.correctedAt = correctedAt;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(UUID approvedBy) {
        this.approvedBy = approvedBy;
    }

    public java.time.Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(java.time.Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
