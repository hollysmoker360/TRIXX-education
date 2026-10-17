package com.schoolsphere.timetable.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** One placement in a timetable version: class (+stream), subject, teacher, room, day and period. */
@Entity
@Table(name = "timetable_entry")
public class TimetableEntry extends TenantEntity {

    UUID versionId;
    UUID classGroupId;
    UUID streamId;
    UUID subjectId;
    UUID teacherMembershipId;
    UUID classroomId;
    int dayOfWeek;
    int periodSeq;
    int span = 1;
    boolean isLocked;

    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
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

    public UUID getTeacherMembershipId() {
        return teacherMembershipId;
    }

    public void setTeacherMembershipId(UUID teacherMembershipId) {
        this.teacherMembershipId = teacherMembershipId;
    }

    public UUID getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(UUID classroomId) {
        this.classroomId = classroomId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(int dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public int getPeriodSeq() {
        return periodSeq;
    }

    public void setPeriodSeq(int periodSeq) {
        this.periodSeq = periodSeq;
    }

    public int getSpan() {
        return span;
    }

    public void setSpan(int span) {
        this.span = span;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }
}
