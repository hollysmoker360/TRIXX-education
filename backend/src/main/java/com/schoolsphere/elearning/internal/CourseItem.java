package com.schoolsphere.elearning.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "course_item")
public class CourseItem extends TenantEntity {
    UUID courseId;
    int position;
    String itemType;
    UUID refId;
    String title;
    boolean required = true;

    public UUID getCourseId() { return courseId; }
    public void setCourseId(UUID v) { this.courseId = v; }
    public int getPosition() { return position; }
    public void setPosition(int v) { this.position = v; }
    public String getItemType() { return itemType; }
    public void setItemType(String v) { this.itemType = v; }
    public UUID getRefId() { return refId; }
    public void setRefId(UUID v) { this.refId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean v) { this.required = v; }
}
