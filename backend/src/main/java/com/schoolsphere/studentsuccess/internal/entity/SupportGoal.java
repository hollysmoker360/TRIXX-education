package com.schoolsphere.studentsuccess.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "support_goal")
public class SupportGoal extends TenantEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "achieved", nullable = false)
    private Boolean achieved = false;

    @Column(name = "achieved_at")
    private LocalDateTime achievedAt;

    @Column(name = "evidence_ref", length = 500)
    private String evidenceRef;

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public Boolean getAchieved() { return achieved; }
    public void setAchieved(Boolean achieved) { this.achieved = achieved; }
    public LocalDateTime getAchievedAt() { return achievedAt; }
    public void setAchievedAt(LocalDateTime achievedAt) { this.achievedAt = achievedAt; }
    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
}
