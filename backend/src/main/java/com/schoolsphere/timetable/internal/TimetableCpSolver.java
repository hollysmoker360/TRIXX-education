package com.schoolsphere.timetable.internal;

import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearArgument;
import com.google.ortools.sat.LinearExpr;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CP-SAT timetable engine (spec 12.1, ADR-020). One model carries every requirement as a Boolean
 * "this requirement is met" literal, which is what makes the three stages cheap:
 *
 * <ol>
 *   <li><b>Feasibility.</b> Solve with the requirement literals asserted. If that is infeasible, the
 *       solver's assumption diagnosis names the requirements that cannot all hold, so the school is
 *       told which ones to relax instead of getting an opaque failure.</li>
 *   <li><b>Optimisation.</b> Release the assumptions and maximise the number of met requirements,
 *       then keep teachers' days compact by penalising free runs longer than two periods.</li>
 *   <li><b>Relaxed draft.</b> If stage one was infeasible the same objective yields the largest
 *       timetable that is still consistent; the requirements it could not place are reported so the
 *       draft is marked as unmet and cannot be published.</li>
 * </ol>
 *
 * <p>The consecutive-lesson rule is the handbook's own form,
 * {@code busy[t] + busy[t+1] <= 1 + doubleStartsAt[t]}, applied per teacher and per adjacent pair
 * inside one session, so a break or lunch ends a session and a double period can never straddle it.
 * Rooms are not part of the model: they are assigned in a second step, which keeps the model small.
 */
@Component
public class TimetableCpSolver {

    private static final Logger log = LoggerFactory.getLogger(TimetableCpSolver.class);
    /** The feasibility stage only has to find one solution, so it gets a short budget. */
    private static final int FEASIBILITY_SECONDS = 10;

    public record Demand(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                         int periodsPerWeek) {
    }

    public record Placement(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                            int day, int period, int span) {
    }

    /**
     * @param unmet    requirements the relaxed model could not place (empty for a complete timetable)
     * @param relaxed  true when the requirements had to be relaxed, so the draft must not be published
     * @param culprits the requirements the assumption diagnosis blames when nothing can be satisfied
     */
    public record Solution(List<Placement> placements, List<Demand> unmet, List<Demand> culprits,
                           String status, boolean optimal, boolean relaxed) {
    }

    /** True when the CP-SAT native library loads in this JVM; callers fall back to the heuristic. */
    public static boolean available() {
        try {
            Loader.loadNativeLibraries();
            return true;
        } catch (Throwable t) {
            log.debug("cp-sat.native.unavailable error={}", t.toString());
            return false;
        }
    }

    public Solution solve(List<Demand> demands, TimetableRules rules, List<Integer> days,
                          List<Placement> locked, int timeLimitSeconds, long seed) {
        Loader.loadNativeLibraries();
        Model model = build(demands, rules, days, locked);
        if (model.candidates.isEmpty()) {
            return new Solution(List.of(), demands, List.of(), "NO_CANDIDATES", false, true);
        }

        // Stage 1: assert every requirement, so an impossible school is diagnosed rather than fudged.
        model.cp.addAssumptions(model.met.toArray(new BoolVar[0]));
        CpSolver probe = newSolver(seed, Math.min(timeLimitSeconds, FEASIBILITY_SECONDS));
        CpSolverStatus feasibility = probe.solve(model.cp);
        boolean hardInfeasible = feasibility == CpSolverStatus.INFEASIBLE
                || feasibility == CpSolverStatus.MODEL_INVALID;
        List<Demand> culprits = hardInfeasible ? diagnose(model, probe, demands) : List.of();
        if (hardInfeasible) {
            model.cp.clearAssumptions();
            log.info("cp-sat.infeasible demands={} culprits={}", demands.size(), culprits.size());
        }

        // Stage 2: place as much as possible, then prefer compact teacher days.
        model.cp.maximize(model.objective());
        CpSolver solver = newSolver(seed, timeLimitSeconds);
        CpSolverStatus status = solver.solve(model.cp);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return new Solution(List.of(), demands, culprits, status.name(), false, hardInfeasible);
        }
        List<Placement> placements = placements(model, solver);
        List<Demand> unmet = new ArrayList<>();
        for (int i = 0; i < model.met.size(); i++) {
            if (!Boolean.TRUE.equals(solver.booleanValue(model.met.get(i)))) {
                unmet.add(demands.get(i));
            }
        }
        return new Solution(placements, unmet, culprits, status.name(),
                status == CpSolverStatus.OPTIMAL, !unmet.isEmpty() || hardInfeasible);
    }

    /** The requirements the solver blames for infeasibility, from the assumption literals. */
    private List<Demand> diagnose(Model model, CpSolver probe, List<Demand> demands) {
        List<Demand> culprits = new ArrayList<>();
        try {
            for (Integer index : probe.sufficientAssumptionsForInfeasibility()) {
                if (index != null && index >= 0 && index < demands.size()) {
                    culprits.add(demands.get(index));
                }
            }
        } catch (RuntimeException e) {
            log.debug("cp-sat.diagnosis.unavailable error={}", e.getMessage());
        }
        return culprits.isEmpty() ? demands : culprits;
    }

    // ----- model -----

    private record Group(UUID classGroupId, UUID streamId) {
    }

    private record Slot(int day, int period) {
    }

    private record TeacherDay(UUID teacher, int day) {
    }

    private static final class Candidate {
        final int demand;
        final int day;
        final int period;
        final int span;
        final BoolVar var;

        Candidate(int demand, int day, int period, int span, BoolVar var) {
            this.demand = demand;
            this.day = day;
            this.period = period;
            this.span = span;
            this.var = var;
        }
    }

    private static final class Model {
        final CpModel cp = new CpModel();
        final List<Demand> demands;
        final List<Candidate> candidates = new ArrayList<>();
        final List<BoolVar> met = new ArrayList<>();
        final List<BoolVar> gaps = new ArrayList<>();
        final List<List<Candidate>> byDemand = new ArrayList<>();

        Model(List<Demand> demands) {
            this.demands = demands;
        }

        LinearArgument objective() {
            LinearArgument meets = LinearExpr.sum(met.toArray(new LinearArgument[0]));
            if (gaps.isEmpty()) {
                return meets;
            }
            // Meeting a requirement always outweighs any amount of tidying a teacher's day.
            long weight = gaps.size() + 1L;
            return LinearExpr.weightedSum(
                    new LinearArgument[]{meets, LinearExpr.sum(gaps.toArray(new LinearArgument[0]))},
                    new long[]{weight, -1});
        }
    }

    private Model build(List<Demand> demands, TimetableRules rules, List<Integer> days,
                        List<Placement> locked) {
        Model model = new Model(demands);
        List<Integer> orderedPeriods = rules.hasLayout()
                ? rules.teachingPeriods().stream().sorted().toList()
                : List.of();
        List<Integer> orderedDays = days.stream()
                .filter(day -> rules.teachingDays().isEmpty() || rules.teachingDays().contains(day))
                .distinct().sorted().toList();

        // Locked placements are occupancy the model must respect and count towards their requirement.
        Map<Group, Set<Slot>> lockedGroup = new HashMap<>();
        Map<TeacherDay, Set<Integer>> lockedTeacher = new HashMap<>();
        Map<TeacherDay, Set<Integer>> lockedDoubleStarts = new HashMap<>();
        int[] lockedPeriods = new int[demands.size()];
        for (Placement lock : locked) {
            if (lock.classGroupId() != null) {
                Set<Slot> slots = lockedGroup.computeIfAbsent(new Group(lock.classGroupId(), lock.streamId()),
                        key -> new HashSet<>());
                for (int offset = 0; offset < Math.max(1, lock.span()); offset++) {
                    slots.add(new Slot(lock.day(), lock.period() + offset));
                }
            }
            if (lock.teacherMembershipId() != null) {
                TeacherDay key = new TeacherDay(lock.teacherMembershipId(), lock.day());
                Set<Integer> slots = lockedTeacher.computeIfAbsent(key, k -> new HashSet<>());
                for (int offset = 0; offset < Math.max(1, lock.span()); offset++) {
                    slots.add(lock.period() + offset);
                }
                if (lock.span() > 1) {
                    lockedDoubleStarts.computeIfAbsent(key, k -> new HashSet<>()).add(lock.period());
                }
            }
            for (int i = 0; i < demands.size(); i++) {
                Demand demand = demands.get(i);
                if (demand.classGroupId().equals(lock.classGroupId())
                        && java.util.Objects.equals(demand.streamId(), lock.streamId())
                        && demand.subjectId().equals(lock.subjectId())
                        && java.util.Objects.equals(demand.teacherMembershipId(), lock.teacherMembershipId())) {
                    lockedPeriods[i] += Math.max(1, lock.span());
                }
            }
        }

        Map<Group, Map<Slot, List<Candidate>>> classCover = new HashMap<>();
        Map<TeacherDay, Map<Integer, List<Candidate>>> teacherCover = new HashMap<>();
        Map<TeacherDay, Map<Integer, List<Candidate>>> teacherDoubleStarts = new HashMap<>();

        for (int i = 0; i < demands.size(); i++) {
            Demand demand = demands.get(i);
            Group group = new Group(demand.classGroupId(), demand.streamId());
            int required = Math.max(0, demand.periodsPerWeek() - lockedPeriods[i]);
            List<Candidate> mine = new ArrayList<>();
            model.byDemand.add(mine);
            for (int day : orderedDays) {
                for (int period : orderedPeriods) {
                    for (int span : new int[]{1, 2}) {
                        if (!fits(span, period, orderedPeriods, rules)) {
                            continue;
                        }
                        if (clashesWithLock(group, demand, day, period, span, lockedGroup, lockedTeacher,
                                lockedDoubleStarts, rules)) {
                            continue;
                        }
                        BoolVar var = model.cp.newBoolVar("d" + i + "_" + day + "_" + period + "_" + span);
                        Candidate candidate = new Candidate(i, day, period, span, var);
                        model.candidates.add(candidate);
                        mine.add(candidate);
                        classCover.computeIfAbsent(group, key -> new HashMap<>())
                                .computeIfAbsent(new Slot(day, period), key -> new ArrayList<>()).add(candidate);
                        if (span == 2) {
                            classCover.get(group).computeIfAbsent(new Slot(day, period + 1), key -> new ArrayList<>())
                                    .add(candidate);
                        }
                        if (demand.teacherMembershipId() != null) {
                            TeacherDay teacherDay = new TeacherDay(demand.teacherMembershipId(), day);
                            Map<Integer, List<Candidate>> covers =
                                    teacherCover.computeIfAbsent(teacherDay, key -> new HashMap<>());
                            covers.computeIfAbsent(period, key -> new ArrayList<>()).add(candidate);
                            if (span == 2) {
                                covers.computeIfAbsent(period + 1, key -> new ArrayList<>()).add(candidate);
                                teacherDoubleStarts.computeIfAbsent(teacherDay, key -> new HashMap<>())
                                        .computeIfAbsent(period, key -> new ArrayList<>()).add(candidate);
                            }
                        }
                    }
                }
            }
            // A requirement is either placed in full or reported unmet; there is no half-placed lesson.
            BoolVar met = model.cp.newBoolVar("met" + i);
            model.met.add(met);
            model.cp.addEquality(LinearExpr.sum(mine.stream().map(c -> (LinearArgument) c.var)
                    .toArray(LinearArgument[]::new)), LinearExpr.term(met, required));
        }

        // A class cannot be in two places at once, and neither can a teacher.
        for (Map<Slot, List<Candidate>> slots : classCover.values()) {
            for (List<Candidate> covering : slots.values()) {
                model.cp.addLessOrEqual(sum(covering), 1);
            }
        }
        for (Map<Integer, List<Candidate>> covers : teacherCover.values()) {
            for (List<Candidate> covering : covers.values()) {
                model.cp.addLessOrEqual(sum(covering), 1);
            }
        }
        addConsecutiveRule(model, rules, teacherCover, teacherDoubleStarts);
        addGapPenalty(model, rules, teacherCover);
        return model;
    }

    /** A span of two needs both periods to exist and to be adjacent with no break between them. */
    private boolean fits(int span, int period, List<Integer> orderedPeriods, TimetableRules rules) {
        if (span == 1) {
            return true;
        }
        return orderedPeriods.contains(period + 1) && rules.inSameSession(period, period + 1);
    }

    /**
     * {@code busy[t] + busy[t+1] <= 1 + doubleStartsAt[t]} for every teacher and every adjacent pair
     * inside one session: the handbook's rule, in the handbook's own form, so a double period is the
     * only way two touching periods can both be busy.
     */
    private void addConsecutiveRule(Model model, TimetableRules rules,
                                    Map<TeacherDay, Map<Integer, List<Candidate>>> teacherCover,
                                    Map<TeacherDay, Map<Integer, List<Candidate>>> teacherDoubleStarts) {
        for (Map.Entry<TeacherDay, Map<Integer, List<Candidate>>> entry : teacherCover.entrySet()) {
            TeacherDay teacherDay = entry.getKey();
            Map<Integer, List<Candidate>> covers = entry.getValue();
            Map<Integer, List<Candidate>> doubles =
                    teacherDoubleStarts.getOrDefault(teacherDay, Map.of());
            for (int period : new LinkedHashSet<>(covers.keySet())) {
                if (!rules.inSameSession(period, period + 1) || !covers.containsKey(period + 1)) {
                    continue;
                }
                List<LinearArgument> terms = new ArrayList<>();
                List<Long> coefficients = new ArrayList<>();
                for (Candidate candidate : covers.getOrDefault(period, List.of())) {
                    terms.add(candidate.var);
                    coefficients.add(1L);
                }
                for (Candidate candidate : covers.getOrDefault(period + 1, List.of())) {
                    terms.add(candidate.var);
                    coefficients.add(1L);
                }
                for (Candidate candidate : doubles.getOrDefault(period, List.of())) {
                    terms.add(candidate.var);
                    coefficients.add(-1L);
                }
                if (terms.isEmpty()) {
                    continue;
                }
                terms.add(LinearExpr.constant(1));
                coefficients.add(-1L);
                model.cp.addLessOrEqual(
                        LinearExpr.weightedSum(terms.toArray(new LinearArgument[0]),
                                coefficients.stream().mapToLong(Long::longValue).toArray()),
                        0);
            }
        }
    }

    /**
     * Soft goal (spec 12.1): three consecutive free periods inside a session is a gap worth avoiding,
     * so each such window can raise a penalty literal that the objective minimises. Windows that
     * would straddle a break are never created, because a break is not a gap in a teacher's day.
     */
    private void addGapPenalty(Model model, TimetableRules rules,
                               Map<TeacherDay, Map<Integer, List<Candidate>>> teacherCover) {
        List<List<Integer>> sessions = rules.sessions();
        for (Map.Entry<TeacherDay, Map<Integer, List<Candidate>>> entry : teacherCover.entrySet()) {
            TeacherDay teacherDay = entry.getKey();
            Map<Integer, List<Candidate>> covers = entry.getValue();
            for (List<Integer> session : sessions) {
                for (int index = 0; index + 2 < session.size(); index++) {
                    List<LinearArgument> free = new ArrayList<>();
                    for (int offset = 0; offset < 3; offset++) {
                        List<Candidate> covering =
                                covers.getOrDefault(session.get(index + offset), List.of());
                        // free = 1 - busy, so the three slots add up to three only when all are free.
                        free.add(covering.isEmpty() ? LinearExpr.constant(1)
                                : LinearExpr.affine(sum(covering), -1, 1));
                    }
                    BoolVar gap = model.cp.newBoolVar("gap_" + teacherDay.hashCode() + "_" + index);
                    // freeSum - 3 * gap <= 2 forces gap to one exactly when all three periods are free.
                    model.cp.addLessOrEqual(
                            LinearExpr.weightedSum(
                                    new LinearArgument[]{LinearExpr.sum(free.toArray(new LinearArgument[0])),
                                            gap},
                                    new long[]{1, -3}),
                            2);
                    model.gaps.add(gap);
                }
            }
        }
    }

    private boolean clashesWithLock(Group group, Demand demand, int day, int period, int span,
                                    Map<Group, Set<Slot>> lockedGroup,
                                    Map<TeacherDay, Set<Integer>> lockedTeacher,
                                    Map<TeacherDay, Set<Integer>> lockedDoubleStarts, TimetableRules rules) {
        Set<Slot> groupSlots = lockedGroup.getOrDefault(group, Set.of());
        for (int offset = 0; offset < span; offset++) {
            if (groupSlots.contains(new Slot(day, period + offset))) {
                return true;
            }
        }
        if (demand.teacherMembershipId() == null) {
            return false;
        }
        TeacherDay teacherDay = new TeacherDay(demand.teacherMembershipId(), day);
        Set<Integer> teacherSlots = lockedTeacher.getOrDefault(teacherDay, Set.of());
        Set<Integer> doubles = lockedDoubleStarts.getOrDefault(teacherDay, Set.of());
        for (int offset = 0; offset < span; offset++) {
            if (teacherSlots.contains(period + offset)) {
                return true;
            }
        }
        int before = period - 1;
        if (teacherSlots.contains(before) && rules.inSameSession(before, period) && !doubles.contains(before)) {
            return true;
        }
        int after = period + span;
        return teacherSlots.contains(after) && rules.inSameSession(period + span - 1, after);
    }

    private static LinearArgument sum(List<Candidate> candidates) {
        return LinearExpr.sum(candidates.stream().map(c -> (LinearArgument) c.var).toArray(LinearArgument[]::new));
    }

    private static CpSolver newSolver(long seed, int timeLimitSeconds) {
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(Math.max(1, timeLimitSeconds));
        solver.getParameters().setRandomSeed((int) seed);
        return solver;
    }

    private List<Placement> placements(Model model, CpSolver solver) {
        List<Placement> placements = new ArrayList<>();
        for (Candidate candidate : model.candidates) {
            if (solver.value(candidate.var) == 1) {
                Demand demand = model.demands.get(candidate.demand);
                placements.add(new Placement(demand.classGroupId(), demand.streamId(), demand.subjectId(),
                        demand.teacherMembershipId(), candidate.day, candidate.period, candidate.span));
            }
        }
        return placements;
    }
}
