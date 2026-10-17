package com.schoolsphere.timetable.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** A named timetable for a term; exactly one version per term may be published (spec 12.1). */
@Entity
@Table(name = "timetable_version")
public class TimetableVersion extends TenantEntity {

    UUID academicYearId;
    UUID termId;
    String name;
    String state = "draft";
    UUID authorMembershipId;
    String note;
    Long solverSeed;
    BigDecimal solverScore;
    int unmetRequirements = 0;

    @Column(name = "constraint_report", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    Map<String, Object> constraintReport = new LinkedHashMap<>();

    Instant validatedAt;
    Instant publishedAt;
    Instant archivedAt;

    public UUID getAcademicYearId() {
        return academicYearId;
    }

    public void setAcademicYearId(UUID academicYearId) {
        this.academicYearId = academicYearId;
    }

    public UUID getTermId() {
        return termId;
    }

    public void setTermId(UUID termId) {
        this.termId = termId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public UUID getAuthorMembershipId() {
        return authorMembershipId;
    }

    public void setAuthorMembershipId(UUID authorMembershipId) {
        this.authorMembershipId = authorMembershipId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getSolverSeed() {
        return solverSeed;
    }

    public void setSolverSeed(Long solverSeed) {
        this.solverSeed = solverSeed;
    }

    public BigDecimal getSolverScore() {
        return solverScore;
    }

    public void setSolverScore(BigDecimal solverScore) {
        this.solverScore = solverScore;
    }

    public int getUnmetRequirements() {
        return unmetRequirements;
    }

    public void setUnmetRequirements(int unmetRequirements) {
        this.unmetRequirements = unmetRequirements;
    }

    public Map<String, Object> getConstraintReport() {
        return constraintReport;
    }

    public void setConstraintReport(Map<String, Object> constraintReport) {
        this.constraintReport = constraintReport;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
