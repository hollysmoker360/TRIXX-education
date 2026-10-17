package com.schoolsphere.featureflags.internal;

import com.schoolsphere.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A row of the {@code feature_flag} table (V2). Unlike tenant entities the school id is nullable:
 * {@code NULL} means a global flag visible to every school. This entity therefore extends
 * {@link BaseEntity} directly rather than {@link TenantEntity}.
 */
@Entity
@Table(name = "feature_flag")
public class FeatureFlagEntity extends BaseEntity {

    @Column(name = "flag_key", nullable = false)
    String flagKey;

    @Column(name = "school_id")
    UUID schoolId;

    @Column(nullable = false)
    boolean enabled;

    @Column(name = "payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    Map<String, Object> payload = new LinkedHashMap<>();

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}
