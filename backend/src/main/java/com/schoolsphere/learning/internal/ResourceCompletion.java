package com.schoolsphere.learning.internal;

import com.schoolsphere.common.util.Uuid7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Per-student completion mark of a learning resource. */
@Entity
@Table(name = "resource_completion")
public class ResourceCompletion {

    @Id
    UUID id;
    UUID schoolId;
    UUID resourceId;
    UUID studentId;
    Instant completedAt;

    @PrePersist
    void onPersist() {
        if (id == null) {
            id = Uuid7.generate();
        }
        if (completedAt == null) {
            completedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
