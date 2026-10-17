package com.schoolsphere.academics.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "stream")
public class Stream extends TenantEntity {

    UUID classGroupId;
    String name;

    public UUID getClassGroupId() {
        return classGroupId;
    }

    public void setClassGroupId(UUID classGroupId) {
        this.classGroupId = classGroupId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
