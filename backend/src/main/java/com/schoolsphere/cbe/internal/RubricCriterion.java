package com.schoolsphere.cbe.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rubric_criterion")
public class RubricCriterion extends TenantEntity {
    UUID rubricId;
    String title;
    String description;
    List<String> levels = new java.util.ArrayList<>();

    public UUID getRubricId() { return rubricId; }
    public void setRubricId(UUID v) { this.rubricId = v; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public List<String> getLevels() { return levels; }
    public void setLevels(List<String> v) { this.levels = v; }
}
