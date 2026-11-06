package com.schoolsphere.studentsuccess.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "student_progress_profile")
public class StudentProgressProfile extends TenantEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "overall_risk_level", nullable = false, length = 20)
    private String overallRiskLevel = "low";

    @Column(name = "attendance_concern", nullable = false)
    private Boolean attendanceConcern = false;

    @Column(name = "academic_decline", nullable = false)
    private Boolean academicDecline = false;

    @Column(name = "incomplete_work", nullable = false)
    private Boolean incompleteWork = false;

    @Column(name = "last_reviewed_at")
    private LocalDate lastReviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public String getOverallRiskLevel() { return overallRiskLevel; }
    public void setOverallRiskLevel(String overallRiskLevel) { this.overallRiskLevel = overallRiskLevel; }
    public Boolean getAttendanceConcern() { return attendanceConcern; }
    public void setAttendanceConcern(Boolean attendanceConcern) { this.attendanceConcern = attendanceConcern; }
    public Boolean getAcademicDecline() { return academicDecline; }
    public void setAcademicDecline(Boolean academicDecline) { this.academicDecline = academicDecline; }
    public Boolean getIncompleteWork() { return incompleteWork; }
    public void setIncompleteWork(Boolean incompleteWork) { this.incompleteWork = incompleteWork; }
    public LocalDate getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(LocalDate lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }
    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }
}
