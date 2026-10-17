package com.schoolsphere.elearning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "course_enrollment")
public class CourseEnrollment extends TenantEntity {
    UUID courseId;
    UUID studentId;
    String status = "ACTIVE";
    Instant enrolledAt = Instant.now();
    Instant completedAt;

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID v) { this.courseId = v; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID v) { this.studentId = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Instant getEnrolledAt() { return enrolledAt; }
    public void setEnrolledAt(Instant v) { this.enrolledAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
}
