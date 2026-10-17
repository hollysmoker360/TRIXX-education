package com.schoolsphere.examinations.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_assessment")
public class ExamAssessment extends TenantEntity {
    String title;
    String assessmentType = "EXAM";
    UUID subjectId;
    UUID classGroupId;
    UUID termId;
    BigDecimal totalMarks;
    BigDecimal passMark;
    Integer durationMinutes;
    String instructions;
    String status = "DRAFT";
    UUID approvedBy;
    Instant approvedAt;
    UUID resultsPublishedBy;
    Instant resultsPublishedAt;

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getAssessmentType() { return assessmentType; }
    public void setAssessmentType(String v) { this.assessmentType = v; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID v) { this.subjectId = v; }
    public UUID getClassGroupId() { return classGroupId; }
    public void setClassGroupId(UUID v) { this.classGroupId = v; }
    public UUID getTermId() { return termId; }
    public void setTermId(UUID v) { this.termId = v; }
    public BigDecimal getTotalMarks() { return totalMarks; }
    public void setTotalMarks(BigDecimal v) { this.totalMarks = v; }
    public BigDecimal getPassMark() { return passMark; }
    public void setPassMark(BigDecimal v) { this.passMark = v; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer v) { this.durationMinutes = v; }
    public String getInstructions() { return instructions; }
    public void setInstructions(String v) { this.instructions = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID v) { this.approvedBy = v; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant v) { this.approvedAt = v; }
    public UUID getResultsPublishedBy() { return resultsPublishedBy; }
    public void setResultsPublishedBy(UUID v) { this.resultsPublishedBy = v; }
    public Instant getResultsPublishedAt() { return resultsPublishedAt; }
    public void setResultsPublishedAt(Instant v) { this.resultsPublishedAt = v; }
}
