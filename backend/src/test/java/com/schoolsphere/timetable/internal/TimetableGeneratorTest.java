package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The generator must never emit a timetable the independent validator rejects (spec 12.10). These
 * tests check the solver's output through {@link TimetableValidator} rather than trusting the
 * solver's internal bookkeeping, so a placement bug cannot hide behind matching logic.
 */
class TimetableGeneratorTest {

    private final TimetableGenerator generator = new TimetableGenerator();
    private final TimetableValidator validator = new TimetableValidator();

    private static final List<Integer> DAYS = List.of(1, 2, 3, 4, 5);
    private static final List<Integer> PERIODS = List.of(1, 2, 3, 4, 5, 6, 7, 8);

    @Test
    void generatedTimetableSatisfiesEveryHardConstraint() {
        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        List<TimetableGenerator.Demand> demands = List.of(
                new TimetableGenerator.Demand(classA, null, subject, teacherA, 10),
                new TimetableGenerator.Demand(classB, null, subject, teacherB, 10));

        TimetableRules rules = TimetableRules.contiguous(1, 8);
        TimetableGenerator.Result result = generator.generate(demands, List.of(), DAYS, PERIODS,
                List.of(UUID.randomUUID(), UUID.randomUUID()), rules, 42L, 24);

        assertThat(result.placed()).isEqualTo(20);
        assertThat(result.unplaced()).isZero();

        TimetableValidator.Report report = validator.validate(entriesOf(result), rules, Map.of());
        assertThat(report.hardCount()).isZero();
        // Soft guidance (long gaps, workload) is allowed; nothing hard may ever appear.
        assertThat(report.issues()).noneMatch(issue -> issue.severity().equals("hard"));
    }

    @Test
    void teacherNeverGetsTwoLessonsInAdjacentPeriods() {
        // One teacher, one day of two adjacent periods, two periods of work: the second can only be
        // placed if it were allowed to touch the first, so the solver must leave it unplaced.
        UUID classA = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        TimetableRules rules = TimetableRules.contiguous(1, 2);
        List<TimetableGenerator.Demand> demands =
                List.of(new TimetableGenerator.Demand(classA, null, UUID.randomUUID(), teacher, 2));

        TimetableGenerator.Result result = generator.generate(demands, List.of(), List.of(1), List.of(1, 2),
                List.of(), rules, 7L, 24);

        assertThat(result.placed()).isEqualTo(1);
        assertThat(result.unplaced()).isEqualTo(1);
        assertThat(validator.validate(entriesOf(result), rules, Map.of()).hardCount()).isZero();
    }

    @Test
    void doublesAreStillAllowedToOccupyAdjacentPeriods() {
        // The rule exempts a double period, so a locked double must survive repair and validation.
        UUID classA = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        TimetableRules rules = TimetableRules.contiguous(1, 4);
        TimetableGenerator.Placement locked =
                new TimetableGenerator.Placement(classA, null, subject, teacher, null, 1, 1, 2);
        List<TimetableGenerator.Demand> demands =
                List.of(new TimetableGenerator.Demand(classA, null, subject, teacher, 2));

        TimetableGenerator.Result result = generator.generate(demands, List.of(locked), List.of(1),
                List.of(1, 2, 3, 4), List.of(), rules, 11L, 8);

        assertThat(entriesOf(result)).anyMatch(entry -> entry.getSpan() == 2 && entry.getPeriodSeq() == 1);
        assertThat(validator.validate(entriesOf(result), rules, Map.of())
                .issues().stream().filter(issue -> issue.kind().equals("teacher_consecutive"))).isEmpty();
    }

    @Test
    void solverPrefersCompactTeacherDays() {
        // Four lessons in an eight-period day can be spread with no gap longer than two periods, so
        // the solver's gap objective should find such a layout.
        UUID classA = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        TimetableRules rules = TimetableRules.contiguous(1, 8);
        List<TimetableGenerator.Demand> demands =
                List.of(new TimetableGenerator.Demand(classA, null, subject, teacher, 4));

        TimetableGenerator.Result result = generator.generate(demands, List.of(), List.of(1), PERIODS,
                List.of(), rules, 5L, 60);

        assertThat(result.placed()).isEqualTo(4);
        assertThat(validator.validate(entriesOf(result), rules, Map.of()).issues())
                .noneMatch(issue -> issue.kind().equals("teacher_long_gap"));
    }

    @Test
    void theSameSeedAlwaysProducesTheSameTimetable() {
        UUID classA = UUID.randomUUID();
        UUID teacher = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        TimetableRules rules = TimetableRules.contiguous(1, 4);
        List<TimetableGenerator.Demand> demands =
                List.of(new TimetableGenerator.Demand(classA, null, subject, teacher, 4));

        TimetableGenerator.Result first = generator.generate(demands, List.of(), List.of(1, 2), List.of(1, 2, 3, 4),
                List.of(), rules, 99L, 12);
        TimetableGenerator.Result second = generator.generate(demands, List.of(), List.of(1, 2), List.of(1, 2, 3, 4),
                List.of(), rules, 99L, 12);

        assertThat(first.placements()).isEqualTo(second.placements());
    }

    private List<TimetableEntry> entriesOf(TimetableGenerator.Result result) {
        List<TimetableEntry> entries = new ArrayList<>();
        for (TimetableGenerator.Placement placement : result.placements()) {
            TimetableEntry entry = new TimetableEntry();
            entry.setClassGroupId(placement.classGroupId());
            entry.setStreamId(placement.streamId());
            entry.setSubjectId(placement.subjectId());
            entry.setTeacherMembershipId(placement.teacherMembershipId());
            entry.setClassroomId(placement.classroomId());
            entry.setDayOfWeek(placement.day());
            entry.setPeriodSeq(placement.period());
            entry.setSpan(placement.span());
            entries.add(entry);
        }
        return entries;
    }
}
