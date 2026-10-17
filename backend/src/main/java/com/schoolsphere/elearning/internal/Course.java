package com.schoolsphere.elearning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course")
public class Course extends TenantEntity {
    String title;
    String description;
    UUID subjectId;
    UUID classGroupId;
    String status = "DRAFT";
    Instant publishedAt;

    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID v) { this.subjectId = v; }
    public UUID getClassGroupId() { return classGroupId; }
    public void setClassGroupId(UUID v) { this.classGroupId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant v) { this.publishedAt = v; }
}
