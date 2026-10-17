package com.schoolsphere.learning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class Assignment extends TenantEntity {

    String title;
    String instructions;
    UUID subjectId;
    UUID classGroupId;
    UUID streamId;
    UUID termId;
    UUID teacherMembershipId;
    Instant openAt;
    Instant dueAt;
    String latePolicy = "accepted";
    BigDecimal maxMarks = BigDecimal.valueOf(100);

    @Column(name = "rubric", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    Map<String, Object> rubric;

    @Column(name = "audience", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    Map<String, Object> audience = new LinkedHashMap<>();

    @Column(name = "attachment_resource_ids", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    List<String> attachmentResourceIds = new ArrayList<>();

    boolean gradesPublished;
    String state = "draft";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getClassGroupId() {
        return classGroupId;
    }

    public void setClassGroupId(UUID classGroupId) {
        this.classGroupId = classGroupId;
    }

    public UUID getStreamId() {
        return streamId;
    }

    public void setStreamId(UUID streamId) {
        this.streamId = streamId;
    }

    public UUID getTermId() {
        return termId;
    }

    public void setTermId(UUID termId) {
        this.termId = termId;
    }

    public UUID getTeacherMembershipId() {
        return teacherMembershipId;
    }

    public void setTeacherMembershipId(UUID teacherMembershipId) {
        this.teacherMembershipId = teacherMembershipId;
    }

    public Instant getOpenAt() {
        return openAt;
    }

    public void setOpenAt(Instant openAt) {
        this.openAt = openAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getLatePolicy() {
        return latePolicy;
    }

    public void setLatePolicy(String latePolicy) {
        this.latePolicy = latePolicy;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public Map<String, Object> getRubric() {
        return rubric;
    }

    public void setRubric(Map<String, Object> rubric) {
        this.rubric = rubric;
    }

    public Map<String, Object> getAudience() {
        return audience;
    }

    public void setAudience(Map<String, Object> audience) {
        this.audience = audience;
    }

    public List<String> getAttachmentResourceIds() {
        return attachmentResourceIds;
    }

    public void setAttachmentResourceIds(List<String> attachmentResourceIds) {
        this.attachmentResourceIds = attachmentResourceIds;
    }

    public boolean isGradesPublished() {
        return gradesPublished;
    }

    public void setGradesPublished(boolean gradesPublished) {
        this.gradesPublished = gradesPublished;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
