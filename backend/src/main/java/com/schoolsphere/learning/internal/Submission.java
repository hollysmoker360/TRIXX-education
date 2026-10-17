package com.schoolsphere.learning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A student's submission; first final submit wins, teacher reopen allows a resubmission (spec 10.4). */
@Entity
@Table(name = "submission")
public class Submission extends TenantEntity {

    UUID assignmentId;
    UUID studentId;
    String state = "draft";
    String contentText;

    @Column(name = "attachment_resource_ids", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    List<String> attachmentResourceIds = new ArrayList<>();

    Instant submittedAt;
    BigDecimal grade;
    String feedback;
    UUID gradedByMembershipId;
    Instant gradedAt;
    Instant reopenedAt;
    String idempotencyKey;

    public UUID getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(UUID assignmentId) {
        this.assignmentId = assignmentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getContentText() {
        return contentText;
    }

    public void setContentText(String contentText) {
        this.contentText = contentText;
    }

    public List<String> getAttachmentResourceIds() {
        return attachmentResourceIds;
    }

    public void setAttachmentResourceIds(List<String> attachmentResourceIds) {
        this.attachmentResourceIds = attachmentResourceIds;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public BigDecimal getGrade() {
        return grade;
    }

    public void setGrade(BigDecimal grade) {
        this.grade = grade;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public UUID getGradedByMembershipId() {
        return gradedByMembershipId;
    }

    public void setGradedByMembershipId(UUID gradedByMembershipId) {
        this.gradedByMembershipId = gradedByMembershipId;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public void setGradedAt(Instant gradedAt) {
        this.gradedAt = gradedAt;
    }

    public Instant getReopenedAt() {
        return reopenedAt;
    }

    public void setReopenedAt(Instant reopenedAt) {
        this.reopenedAt = reopenedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
