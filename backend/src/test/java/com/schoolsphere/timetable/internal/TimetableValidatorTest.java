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

    private static final TimetableRules RULES = TimetableRules.contiguous(1, 8);
    /** Periods 1-3, then a break, then 5-7: two sessions with no adjacency across the break. */
    private static final TimetableRules RULES_WITH_BREAK = new TimetableRules(
            Set.of(1, 2, 3, 5, 6, 7),
            Set.of(TimetableRules.pairKey(1, 2), TimetableRules.pairKey(2, 3),
                    TimetableRules.pairKey(5, 6), TimetableRules.pairKey(6, 7)),
            java.util.Set.of(1, 2, 3, 4, 5), 1, Map.of());
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
        entry.setId(UUID.randomUUID());
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
                entry(1, 3, CLASS_A, TEACHER_2, ROOM_1)), RULES, Map.of());

        assertThat(report.issues()).isEmpty();
        assertThat(report.hardCount()).isZero();
        assertThat(report.score()).isEqualTo(100);
    }

    @Test
    void detectsClassClash() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(2, 3, CLASS_A, TEACHER_1, ROOM_1),
                entry(2, 3, CLASS_A, TEACHER_2, null)), RULES, Map.of());

        assertThat(report.hardCount()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("class_clash") && i.severity().equals("hard"));
        assertThat(report.score()).isEqualTo(85);
    }

    @Test
    void detectsTeacherDoubleBooking() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(3, 4, CLASS_A, TEACHER_1, ROOM_1),
                entry(3, 4, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_clash"));
        assertThat(report.hardCount()).isEqualTo(1);
    }

    @Test
    void detectsRoomDoubleBooking() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(4, 1, CLASS_A, TEACHER_1, ROOM_1),
                entry(4, 1, CLASS_B, TEACHER_2, ROOM_1)), RULES, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("room_clash"));
        assertThat(report.hardCount()).isEqualTo(1);
    }

    @Test
    void backendOfDoublePeriodOnlyClashesWhenItOverlaps() {
        // A double period spanning 1-2 must clash with a single entry in period 2, not period 3.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 3, CLASS_B, TEACHER_2, null)), RULES, Map.of());

        assertThat(report.issues()).isEmpty();

        TimetableValidator.Report overlap = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 2, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(overlap.issues()).anyMatch(i -> i.kind().equals("teacher_clash"));
    }

    @Test
    void detectsPeriodOutsideTeachingRangeAndEndOfDayOverflow() {
        // Period 9 is not a teaching period; the second entry sits in a non-teaching period 24 and
        // also overflows the day (24 + 2 - 1 > 24), so both range rules fire for it.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(5, 9, CLASS_A, TEACHER_1, ROOM_1),
                entry(5, 24, 2, CLASS_B, TEACHER_2, null)), RULES, Map.of());

        assertThat(report.issues().stream().filter(i -> i.kind().equals("period_out_of_range"))).hasSize(3);
        assertThat(report.hardCount()).isEqualTo(3);
    }

    @Test
    void emptyPeriodSetSkipsRangeCheck() {
        TimetableRules noLayout = new TimetableRules(Set.of(), Set.of(), null, 1, Map.of());
        TimetableValidator.Report report = validator.validate(List.of(
                entry(5, 9, CLASS_A, TEACHER_1, ROOM_1)), noLayout, Map.of());

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void flagsDailyAndWeeklyTeacherLoadAsSoftIssues() {
        // 9 periods on one day for the same teacher: exceeds the daily soft limit of 8. The
        // consecutive-lesson rule is relaxed here so this test stays about workload only.
        var entries = new java.util.ArrayList<TimetableEntry>();
        for (int period = 1; period <= 9; period++) {
            entries.add(entry(1, period, CLASS_A, TEACHER_1, null));
        }

        TimetableValidator.Report report = validator.validate(entries,
                TimetableRules.contiguous(1, 10).withMaxConsecutivePeriods(9), Map.of(TEACHER_1, 5));

        assertThat(report.hardCount()).isZero();
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_daily_load"));
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_weekly_load"));
        assertThat(report.softCount()).isEqualTo(2);
    }

    @Test
    void hardAndSoftIssuesReduceScore() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 1, CLASS_A, TEACHER_1, ROOM_1)), RULES, Map.of());

        // Overlap of two identical entries: class clash + teacher clash + room clash = 3 hard.
        assertThat(report.hardCount()).isEqualTo(3);
        assertThat(report.score()).isEqualTo(100 - 3 * 15.0);
    }

    // ----- consecutive-lesson rule (school requirement) -----

    @Test
    void allowsBackToBackSlotsInsideOneDoublePeriod() {
        // A double is a single lesson even though it covers two adjacent periods.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1)), RULES, Map.of());

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void rejectsTwoSeparateLessonsInAdjacentPeriods() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 3, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 4, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.hardCount()).isEqualTo(1);
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_consecutive"));
    }

    @Test
    void rejectsADoubleFollowedImmediatelyByASingle() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 3, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_consecutive"));
    }

    @Test
    void rejectsTwoDoublesInARow() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 3, 2, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_consecutive"));
    }

    @Test
    void allowsABreakBetweenTwoLessons() {
        // Periods 4 is a break, so periods 3 and 5 do not touch.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 3, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 5, CLASS_B, TEACHER_1, null)), RULES_WITH_BREAK, Map.of());

        assertThat(report.issues()).isEmpty();
    }

    @Test
    void rejectsLessonsThatTouchInsideASession() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 5, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 6, CLASS_B, TEACHER_1, null)), RULES_WITH_BREAK, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_consecutive"));
    }

    @Test
    void flagsADoubleThatSpansABreak() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 3, 2, CLASS_A, TEACHER_1, ROOM_1)), RULES_WITH_BREAK, Map.of());

        assertThat(report.issues()).anyMatch(i -> i.kind().equals("double_spans_break"));
        assertThat(report.hardCount()).isEqualTo(1);
    }

    @Test
    void perTeacherOverrideRelaxesTheRuleForOneTeacherOnly() {
        TimetableRules relaxed = RULES.withTeacherLimits(Map.of(TEACHER_1, 3));

        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 3, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 4, CLASS_B, TEACHER_1, null),
                entry(1, 5, CLASS_A, TEACHER_2, null),
                entry(1, 6, CLASS_B, TEACHER_2, null)), relaxed, Map.of());

        // TEACHER_1 has an override of 3 so their three-in-a-row run is fine; TEACHER_2 still may not
        // sit in adjacent periods.
        assertThat(report.issues()).hasSize(1);
        assertThat(report.issues().get(0).kind()).isEqualTo("teacher_consecutive");
        assertThat(report.issues().get(0).entryId()).isNotNull();
    }

    @Test
    void consecutiveIssuesCanBeCheckedForASingleCandidateEdit() {
        // The editor path: a probe entry plus the rest of the timetable.
        List<TimetableValidator.Issue> issues = validator.consecutiveIssues(List.of(
                entry(1, 2, CLASS_A, TEACHER_1, ROOM_1),
                entry(1, 3, CLASS_B, TEACHER_1, null)), RULES);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).message()).contains("day 1").contains("from period 2");
        assertThat(issues.get(0).entryId()).isNotNull();
        assertThat(issues.get(0).otherEntryId()).isNotNull();
    }

    // ----- gap guidance (school requirement: avoid gaps longer than two periods) -----

    @Test
    void flagsALongGapInATeachersDayAsSoftGuidance() {
        // Periods 1 and 5 leave three free periods (2, 3 and 4) in between.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, null),
                entry(1, 5, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.hardCount()).isZero();
        assertThat(report.issues()).anyMatch(i -> i.kind().equals("teacher_long_gap")
                && i.severity().equals("soft"));
    }

    @Test
    void doesNotFlagAShortGap() {
        // Two free periods (2 and 3) is inside the guidance.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, null),
                entry(1, 4, CLASS_B, TEACHER_1, null)), RULES, Map.of());

        assertThat(report.issues()).noneMatch(i -> i.kind().equals("teacher_long_gap"));
    }

    @Test
    void aGapAcrossABreakIsNotCounted() {
        // Period 3 ends the morning and period 5 starts the afternoon, so the free period 4 is a
        // break rather than a gap in the teacher's day.
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 3, CLASS_A, TEACHER_1, null),
                entry(1, 5, CLASS_B, TEACHER_1, null)), RULES_WITH_BREAK, Map.of());

        assertThat(report.issues()).noneMatch(i -> i.kind().equals("teacher_long_gap"));
    }

    @Test
    void adjacentLessonsHaveNoGap() {
        TimetableValidator.Report report = validator.validate(List.of(
                entry(1, 1, CLASS_A, TEACHER_1, null),
                entry(1, 2, CLASS_B, TEACHER_1, null)), TimetableRules.contiguous(1, 8).withMaxConsecutivePeriods(2),
                Map.of());

        assertThat(report.issues()).noneMatch(i -> i.kind().equals("teacher_long_gap"));
    }
}
