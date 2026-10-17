package com.schoolsphere.cbe.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "rubric")
public class Rubric extends TenantEntity {
    String title;
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
}
