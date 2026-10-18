package com.schoolsphere.grades.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "assessment")
public class Assessment extends TenantEntity {

    @Column(name = "class_group_id", nullable = false)
    private UUID classGroupId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_membership_id", nullable = false)
    private UUID teacherMembershipId;

    @Column(name = "term_id", nullable = false)
    private UUID termId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal weight;

    @Column(name = "date_assessed", nullable = false)
    private LocalDate dateAssessed;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "grading_scale_id")
    private UUID gradingScaleId;

    @Column(name = "published", nullable = false)
    private Boolean published = false;

    @Column(name = "published_at")
    private java.time.Instant publishedAt;

    public UUID getClassGroupId() {
        return classGroupId;
    }

    public void setClassGroupId(UUID classGroupId) {
        this.classGroupId = classGroupId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getTeacherMembershipId() {
        return teacherMembershipId;
    }

    public void setTeacherMembershipId(UUID teacherMembershipId) {
        this.teacherMembershipId = teacherMembershipId;
    }

    public UUID getTermId() {
        return termId;
    }

    public void setTermId(UUID termId) {
        this.termId = termId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public LocalDate getDateAssessed() {
        return dateAssessed;
    }

    public void setDateAssessed(LocalDate dateAssessed) {
        this.dateAssessed = dateAssessed;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public UUID getGradingScaleId() {
        return gradingScaleId;
    }

    public void setGradingScaleId(UUID gradingScaleId) {
        this.gradingScaleId = gradingScaleId;
    }

    public Boolean getPublished() {
        return published;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public java.time.Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(java.time.Instant publishedAt) {
        this.publishedAt = publishedAt;
    }
}
