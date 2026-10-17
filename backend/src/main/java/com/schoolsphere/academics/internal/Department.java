package com.schoolsphere.academics.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "department")
public class Department extends TenantEntity {

    String name;
    String code;
    UUID headMembershipId;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UUID getHeadMembershipId() {
        return headMembershipId;
    }

    public void setHeadMembershipId(UUID headMembershipId) {
        this.headMembershipId = headMembershipId;
    }
}
