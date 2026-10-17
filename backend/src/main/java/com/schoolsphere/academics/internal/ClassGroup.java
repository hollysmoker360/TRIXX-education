package com.schoolsphere.academics.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "class_group")
public class ClassGroup extends TenantEntity {

    String name;
    Integer level;
    UUID departmentId;
    UUID classTeacherMembershipId;
    Integer capacity;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(UUID departmentId) {
        this.departmentId = departmentId;
    }

    public UUID getClassTeacherMembershipId() {
        return classTeacherMembershipId;
    }

    public void setClassTeacherMembershipId(UUID classTeacherMembershipId) {
        this.classTeacherMembershipId = classTeacherMembershipId;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }
}
