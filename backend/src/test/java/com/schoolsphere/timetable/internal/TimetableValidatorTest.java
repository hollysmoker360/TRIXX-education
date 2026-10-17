package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The validator must independently catch every hard-constraint violation in a timetable (spec 12.3),
 * including timetables produced by the generator. These tests deliberately construct entries by hand
 * so they do not share logic with the generator.
 */
class TimetableValidatorTest {

    private final TimetableValidator validator = new TimetableValidator();

    private static final Set<Integer> PERIODS = Set.of(1, 2, 3, 4, 5, 6, 7, 8);
    private static final UUID CLASS_A = UUID.randomUUID();
    private static final UUID CLASS_B = UUID.randomUUID();
    private static final UUID TEACHER_1 = UUID.randomUUID();
    private static final UUID TEACHER_2 = UUID.randomUUID();
    private static final UUID ROOM_1 = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    private TimetableEntry entry(int day, int period, UUID classId, UUID teacher, UUID room) {
        return entry(day, period, 1, classId, teacher, room);
    }

    private TimetableEntry entry(int day, int period, int span, UUID classId, UUID teacher, UUID room) {
        TimetableEntry entry = new TimetableEntry();
        entry.setDayOfWeek(day);
        entry.setPeriodSeq(period);
        entry.setSpan(span);
        entry.setClassGroupId(classId);
        entry.setTeacherMembershipId(teacher);
        entry.setClassroomId(room);
        entry.setSubjectId(SUBJECT);
        return entry;
    }

    @Test
    void cleanTimetableHasNoIssues() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 1, CLASS_B, TEACHER_2, null),
                entry(1, 2, CLASS_A, TEACHER_2, ROOM_1)), PERIODS, Map.of());

        assertThat(report.issues()).isEmpty();
        assertThat(report.hardCount()).isZero();
        assertThat(report.score()).isEqualTo(100);
    }

    @Test
    void detectsClassClash() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(2, 3, CLASS_A, TEACHER_1, ROOM_1),
                entry(2, 3, CLASS_A, TEACHER_2, null)), PERIODS, Map.of());

        assertThat(report.hardCount()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("class_clash") && i.severity().equals("hard"));
        assertThat(report.score()).isEqualTo(85);
    }

    @Test
    void detectsTeacherDoubleBooking() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(3, 4, CLASS_A, TEACHER_1, ROOM_1),
                entry(3, 4, CLASS_B, TEACHER_1, null)), PERIODS, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_clash"));
        assertThat(report.hardCount()).isEqualTo(1);
    }

    @Test
    void detectsRoomDoubleBooking() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(4, 1, CLASS_A, TEACHER_1, ROOM_1),
                entry(4, 1, CLASS_B, TEACHER_2, ROOM_1)), PERIODS, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("room_clash"));
        assertThat(report.hardCount()).isEqualTo(1);
    }

    @Test
    void backendOfDoublePeriodOnlyClashesWhenItOverlaps() {
        // A double period spanning 1-2 must clash with a single entry in period 2, not period 3.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 3, CLASS_B, TEACHER_2, null)), PERIODS, Map.of());

        assertThat(report.issues()).isEmpty();

        TimetableValidator.Report overlap = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 2, CLASS_B, TEACHER_1, null)), PERIODS, Map.of());

        assertThat(overlap.issues()).anyMatch(i -> i.kind().equals("teacher_clash"));
    }

    @Test
    void detectsPeriodOutsideTeachingRangeAndEndOfDayOverflow() {
        // Period 9 is not a teaching period; the second entry sits in a non-teaching period 24 and
        // also overflows the day (24 + 2 - 1 > 24), so both range rules fire for it.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(5, 9, CLASS_A, TEACHER_1, ROOM_1),
                entry(5, 24, 2, CLASS_B, TEACHER_2, null)), PERIODS, Map.of());

        assertThat(report.issues().stream().filter(i -> i.kind().equals("period_out_of_range"))).hasSize(3);
        assertThat(report.hardCount()).isEqualTo(3);
    }

    @Test
    void emptyPeriodSetSkipsRangeCheck() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(5, 9, CLASS_A, TEACHER_1, ROOM_1)), Set.of(), Map.of());

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void flagsDailyAndWeeklyTeacherLoadAsSoftIssues() {
        // 9 periods on one day for the same teacher: exceeds the daily soft limit of 8.
        var entries = new java.util.ArrayList<TimetableEntry>();
        for (int period = 1; period <= 9; period++) {
            entries.add(entry(1, period, CLASS_A, TEACHER_1, null));
        }

        TimetableValidator.Report report = validator.validate(entries, Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
                Map.of(TEACHER_1, 5));

        assertThat(report.hardCount()).isZero();
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_daily_load"));
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_weekly_load"));
        assertThat(report.softCount()).isEqualTo(2);
    }

    @Test
    void hardAndSoftIssuesReduceScore() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 1, CLASS_A, TEACHER_1, ROOM_1)), PERIODS, Map.of());

        // Overlap of two identical entries: class clash + teacher clash + room clash = 3 hard.
        assertThat(report.hardCount()).isEqualTo(3);
        assertThat(report.score()).isEqualTo(100 - 3 * 15.0);
    }
}
