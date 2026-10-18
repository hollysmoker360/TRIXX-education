package com.schoolsphere.studentsuccess.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "progress_note")
public class ProgressNote extends TenantEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "author_membership_id", nullable = false)
    private UUID authorMembershipId;

    @Column(name = "note_type", nullable = false, length = 40)
    private String noteType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_confidential", nullable = false)
    private Boolean isConfidential = false;

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public UUID getStudentId() { return studentId; }
    public void setStudentId(UUID studentId) { this.studentId = studentId; }
    public UUID getAuthorMembershipId() { return authorMembershipId; }
    public void setAuthorMembershipId(UUID authorMembershipId) { this.authorMembershipId = authorMembershipId; }
    public String getNoteType() { return noteType; }
    public void setNoteType(String noteType) { this.noteType = noteType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getIsConfidential() { return isConfidential; }
    public void setIsConfidential(Boolean isConfidential) { this.isConfidential = isConfidential; }
}
