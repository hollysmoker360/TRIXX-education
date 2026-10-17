package com.schoolsphere.people.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "guardian_link")
public class GuardianLink extends TenantEntity {

    UUID parentId;
    UUID studentId;
    String relationship;
    boolean isPrimary;

    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    Map<String, Boolean> visibilities = new LinkedHashMap<>();

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public String getRelationship() {
        return relationship;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void setPrimary(boolean primary) {
        isPrimary = primary;
    }

    public Map<String, Boolean> getVisibilities() {
        return visibilities;
    }

    public void setVisibilities(Map<String, Boolean> visibilities) {
        this.visibilities = visibilities;
    }
}
