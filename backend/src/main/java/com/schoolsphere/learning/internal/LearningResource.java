package com.schoolsphere.learning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "learning_resource")
public class LearningResource extends TenantEntity {

    String title;
    String description;
    String resourceType;
    String topic;
    UUID subjectId;
    UUID classGroupId;
    UUID streamId;
    UUID academicYearId;
    UUID termId;
    String state = "draft";
    Instant publishAt;

    @Column(name = "audience", nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    Map<String, Object> audience = new LinkedHashMap<>();

    int latestVersion = 1;
    UUID uploadedByMembershipId;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Instant getPublishAt() {
        return publishAt;
    }

    public void setPublishAt(Instant publishAt) {
        this.publishAt = publishAt;
    }

    public Map<String, Object> getAudience() {
        return audience;
    }

    public void setAudience(Map<String, Object> audience) {
        this.audience = audience;
    }

    public int getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(int latestVersion) {
        this.latestVersion = latestVersion;
    }

    public UUID getUploadedByMembershipId() {
        return uploadedByMembershipId;
    }

    public void setUploadedByMembershipId(UUID uploadedByMembershipId) {
        this.uploadedByMembershipId = uploadedByMembershipId;
    }
}
