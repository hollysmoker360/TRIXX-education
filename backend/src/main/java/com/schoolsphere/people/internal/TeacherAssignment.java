package com.schoolsphere.people.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "teacher_assignment")
public class TeacherAssignment extends TenantEntity {

    UUID teacherId;
    UUID classGroupId;
    UUID streamId;
    UUID subjectId;
    UUID termId;
    Integer periodsPerWeek;

    public UUID getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(UUID teacherId) {
        this.teacherId = teacherId;
    }

    public UUID getClassGroupId() {
        return classGroupId;
    }

    public void setClassGroupId(UUID classGroupId) {
        this.classGroupId = classGroupId;
    }

    public UUID getStreamId() {
        return streamId;
    }

    public void setStreamId(UUID streamId) {
        this.streamId = streamId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(UUID subjectId) {
        this.subjectId = subjectId;
    }

    public UUID getTermId() {
        return termId;
    }

    public void setTermId(UUID termId) {
        this.termId = termId;
    }

    public Integer getPeriodsPerWeek() {
        return periodsPerWeek;
    }

    public void setPeriodsPerWeek(Integer periodsPerWeek) {
        this.periodsPerWeek = periodsPerWeek;
    }
}
