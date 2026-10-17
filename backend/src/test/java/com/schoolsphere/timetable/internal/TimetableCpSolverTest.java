package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.schoolsphere.timetable.internal.RoomAssigner.Assigned;
import com.schoolsphere.timetable.internal.TimetableCpSolver.Demand;
import com.schoolsphere.timetable.internal.TimetableCpSolver.Placement;
import com.schoolsphere.timetable.internal.TimetableCpSolver.Solution;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises the CP-SAT engine against the real native library when it is available. */
class TimetableCpSolverTest {

    private static final TimetableRules RULES = TimetableRules.contiguous(1, 8);

    @Test
    void placesEveryRequirementAndKeepsTeachersFreeOfConsecutiveLessons() {
        Assumptions.assumeTrue(TimetableCpSolver.available(), "CP-SAT native library unavailable");

        UUID classA = UUID.randomUUID();
        UUID classB = UUID.randomUUID();
        UUID teacherA = UUID.randomUUID();
        UUID teacherB = UUID.randomUUID();
        UUID math = UUID.randomUUID();
        UUID english = UUID.randomUUID();

        List<Demand> demands = List.of(
                new Demand(classA, null, math, teacherA, 3),
                new Demand(classA, null, english, teacherB, 3),
                new Demand(classB, null, math, teacherA, 2),
                new Demand(classB, null, english, teacherB, 2));

        Solution solution = new TimetableCpSolver().solve(demands, RULES, List.of(1, 2, 3, 4, 5), List.of(), 30, 42L);

        assertThat(solution.status()).isIn("OPTIMAL", "FEASIBLE");
        assertThat(solution.unmet()).isEmpty();
        assertThat(solution.placements()).hasSize(3 + 3 + 2 + 2);
        // The same teacher never teaches two consecutive periods unless it is a double.
        for (UUID teacher : List.of(teacherA, teacherB)) {
            for (int day = 1; day <= 5; day++) {
                int[] busy = new int[10];
                for (Placement placement : solution.placements()) {
                    if (!teacher.equals(placement.teacherMembershipId()) || placement.day() != day) {
                        continue;
                    }
                    for (int offset = 0; offset < Math.max(1, placement.span()); offset++) {
                        busy[placement.period() + offset] = 1;
                    }
                }
                for (int period = 1; period < 8; period++) {
                    if (busy[period] == 1 && busy[period + 1] == 1) {
                        final UUID teacherId = teacher;
                        final int checkDay = day;
                        final int checkPeriod = period;
                        // Only allowed when a double period starts at `period`.
                        boolean doubleHere = solution.placements().stream().anyMatch(p ->
                                teacherId.equals(p.teacherMembershipId()) && p.day() == checkDay
                                        && p.period() == checkPeriod && p.span() > 1);
                        assertThat(doubleHere).as("teacher %s day %d period %d", teacher, day, period).isTrue();
                    }
                }
            }
        }
    }

    @Test
    void reportsUnmetRequirementsWhenNothingCanBePlaced() {
        Assumptions.assumeTrue(TimetableCpSolver.available(), "CP-SAT native library unavailable");

        UUID classA = UUID.randomUUID();
        UUID teacherA = UUID.randomUUID();
        UUID math = UUID.randomUUID();

        // Twenty periods a week in an eight-period day is impossible, so the relaxed model must drop it.
        List<Demand> demands = List.of(new Demand(classA, null, math, teacherA, 50));

        Solution solution = new TimetableCpSolver().solve(demands, RULES, List.of(1, 2, 3, 4, 5), List.of(), 30, 7L);

        assertThat(solution.relaxed()).isTrue();
        assertThat(solution.unmet()).hasSize(1);
        assertThat(solution.placements()).isEmpty();
    }

    @Test
    void roomAssignerNeverDoubleBooksARoom() {
        UUID classroom = UUID.randomUUID();
        RoomAssigner.Lesson a = new RoomAssigner.Lesson(UUID.randomUUID(), null, UUID.randomUUID(), null, 1, 1, 1);
        RoomAssigner.Lesson b = new RoomAssigner.Lesson(UUID.randomUUID(), null, UUID.randomUUID(), null, 1, 1, 1);
        List<Assigned> assigned = RoomAssigner.assign(List.of(a, b), List.of(classroom));

        assertThat(assigned).hasSize(2);
        assertThat(assigned.get(0).classroomId()).isEqualTo(classroom);
        assertThat(assigned.get(1).classroomId()).isNull();
        assertThat(RoomAssigner.unassigned(assigned)).isEqualTo(1);
    }
}
