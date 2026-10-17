package com.schoolsphere.learning.internal;

import com.schoolsphere.common.util.Uuid7;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable resource version; restore creates a new version instead of updating one (spec 11.2).
 * The table maps only its own columns (no updated_at/version/deleted_at).
 */
@Entity
@Table(name = "resource_version")
public class ResourceVersion {

    @Id
    UUID id;
    UUID schoolId;
    UUID resourceId;
    int versionNo;
    String storageKey;
    String mimeType;
    Long sizeBytes;
    String checksum;
    String externalUrl;
    String textBody;
    String scanState = "clean";
    UUID uploadedByMembershipId;
    String note;
    Instant createdAt;

    @PrePersist
    void onPersist() {
        if (id == null) {
            id = Uuid7.generate();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(UUID schoolId) {
        this.schoolId = schoolId;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public void setResourceId(UUID resourceId) {
        this.resourceId = resourceId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public void setExternalUrl(String externalUrl) {
        this.externalUrl = externalUrl;
    }

    public String getTextBody() {
        return textBody;
    }

    public void setTextBody(String textBody) {
        this.textBody = textBody;
    }

    public String getScanState() {
        return scanState;
    }

    public void setScanState(String scanState) {
        this.scanState = scanState;
    }

    public UUID getUploadedByMembershipId() {
        return uploadedByMembershipId;
    }

    public void setUploadedByMembershipId(UUID uploadedByMembershipId) {
        this.uploadedByMembershipId = uploadedByMembershipId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
