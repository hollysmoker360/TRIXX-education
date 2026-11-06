package com.schoolsphere.studentsuccess.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "intervention_plan")
public class InterventionPlan extends TenantEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "referral_id")
    private UUID referralId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    @Column(name = "coordinator_membership_id", nullable = false)
    private UUID coordinatorMembershipId;

    @Column(name = "guardian_notified", nullable = false)
    private Boolean guardianNotified = false;

    @Column(name = "guardian_consent")
    private Boolean guardianConsent;

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getReferralId() { return referralId; }
    public void setReferralId(UUID referralId) { this.referralId = referralId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getReviewDate() { return reviewDate; }
    public void setReviewDate(LocalDate reviewDate) { this.reviewDate = reviewDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public UUID getCoordinatorMembershipId() { return coordinatorMembershipId; }
    public void setCoordinatorMembershipId(UUID coordinatorMembershipId) { this.coordinatorMembershipId = coordinatorMembershipId; }
    public Boolean getGuardianNotified() { return guardianNotified; }
    public void setGuardianNotified(Boolean guardianNotified) { this.guardianNotified = guardianNotified; }
    public Boolean getGuardianConsent() { return guardianConsent; }
    public void setGuardianConsent(Boolean guardianConsent) { this.guardianConsent = guardianConsent; }
}
