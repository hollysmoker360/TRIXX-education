package com.schoolsphere.tenants.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "school_membership")
public class SchoolMembership extends TenantEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "title")
    private String title;

    @Column(name = "staff_number")
    private String staffNumber;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "membership_role",
            joinColumns = @JoinColumn(name = "membership_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<SchoolRole> roles = new LinkedHashSet<>();

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStaffNumber() {
        return staffNumber;
    }

    public void setStaffNumber(String staffNumber) {
        this.staffNumber = staffNumber;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void bumpTokenVersion() {
        this.tokenVersion++;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Set<SchoolRole> getRoles() {
        return roles;
    }

    public Set<String> permissionSet() {
        Set<String> permissions = new LinkedHashSet<>();
        for (SchoolRole role : roles) {
            permissions.addAll(role.getPermissions());
        }
        return permissions;
    }
}
