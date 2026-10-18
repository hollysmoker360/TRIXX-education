package com.schoolsphere.studentsuccess.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_referral")
public class SupportReferral extends TenantEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "referred_by", nullable = false)
    private UUID referredBy;

    @Column(name = "referred_to")
    private UUID referredTo;

    @Column(name = "referral_type", nullable = false, length = 40)
    private String referralType;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority = "routine";

    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    @Column(name = "supporting_evidence", columnDefinition = "jsonb")
    private String supportingEvidence;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "open";

    @Column(name = "outcome_notes", length = 2000)
    private String outcomeNotes;

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getReferredBy() { return referredBy; }
    public void setReferredBy(UUID referredBy) { this.referredBy = referredBy; }
    public UUID getReferredTo() { return referredTo; }
    public void setReferredTo(UUID referredTo) { this.referredTo = referredTo; }
    public String getReferralType() { return referralType; }
    public void setReferralType(String referralType) { this.referralType = referralType; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSupportingEvidence() { return supportingEvidence; }
    public void setSupportingEvidence(String supportingEvidence) { this.supportingEvidence = supportingEvidence; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOutcomeNotes() { return outcomeNotes; }
    public void setOutcomeNotes(String outcomeNotes) { this.outcomeNotes = outcomeNotes; }
}
