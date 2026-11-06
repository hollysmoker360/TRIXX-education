package com.schoolsphere.grades.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "assessment_component")
public class AssessmentComponent extends TenantEntity {

    @Column(name = "assessment_id", nullable = false)
    private java.util.UUID assessmentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "max_marks", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxMarks;

    @Column(name = "weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal weight;

    @Column(name = "position", nullable = false)
    private Integer position;

    public java.util.UUID getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(java.util.UUID assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMaxMarks() {
        return maxMarks;
    }

    public void setMaxMarks(BigDecimal maxMarks) {
        this.maxMarks = maxMarks;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }
}
