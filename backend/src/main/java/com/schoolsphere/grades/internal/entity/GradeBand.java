package com.schoolsphere.grades.internal.entity;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "grade_band")
public class GradeBand extends TenantEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "min_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal minScore;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "grade_point", precision = 3, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMinScore() {
        return minScore;
    }

    public void setMinScore(BigDecimal minScore) {
        this.minScore = minScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
    }

    public BigDecimal getGradePoint() {
        return gradePoint;
    }

    public void setGradePoint(BigDecimal gradePoint) {
        this.gradePoint = gradePoint;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }
}
