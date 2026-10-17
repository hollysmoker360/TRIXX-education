package com.schoolsphere.cbe.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "competency")
public class Competency extends TenantEntity {
    UUID curriculumVersionId;
    UUID parentId;
    String code;
    String title;
    String description;
    String status = "DRAFT";
    List<String> evidenceTypes = new java.util.ArrayList<>();

    public UUID getCurriculumVersionId() { return curriculumVersionId; }
    public void setCurriculumVersionId(UUID v) { this.curriculumVersionId = v; }
    public UUID getParentId() { return parentId; }
    public void setParentId(UUID v) { this.parentId = v; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public List<String> getEvidenceTypes() { return evidenceTypes; }
    public void setEvidenceTypes(List<String> v) { this.evidenceTypes = v; }
}
