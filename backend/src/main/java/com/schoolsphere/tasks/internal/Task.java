package com.schoolsphere.tasks.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A personal tasks item; task_key deduplicates generated suggestions per owner (spec 11.3). */
@Entity
@Table(name = "task")
public class Task extends TenantEntity {

    UUID ownerUserId;
    UUID ownerMembershipId;
    String title;
    String description;
    Instant dueAt;
    String priority = "medium";
    String category;
    UUID relatedSubjectId;
    UUID relatedAssignmentId;
    String taskKey;
    Instant reminderAt;
    Instant doneAt;

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getOwnerMembershipId() {
        return ownerMembershipId;
    }

    public void setOwnerMembershipId(UUID ownerMembershipId) {
        this.ownerMembershipId = ownerMembershipId;
    }

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

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public UUID getRelatedSubjectId() {
        return relatedSubjectId;
    }

    public void setRelatedSubjectId(UUID relatedSubjectId) {
        this.relatedSubjectId = relatedSubjectId;
    }

    public UUID getRelatedAssignmentId() {
        return relatedAssignmentId;
    }

    public void setRelatedAssignmentId(UUID relatedAssignmentId) {
        this.relatedAssignmentId = relatedAssignmentId;
    }

    public String getTaskKey() {
        return taskKey;
    }

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public Instant getReminderAt() {
        return reminderAt;
    }

    public void setReminderAt(Instant reminderAt) {
        this.reminderAt = reminderAt;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }
}
