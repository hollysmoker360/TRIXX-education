package com.schoolsphere.timetable.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Constraint checks for a timetable version (spec 12.1). Hard issues block publishing; soft
 * issues are workload guidance. All checks are pure over the entry list plus the loaded rules.
 *
 * <p>This is deliberately a separate implementation from {@link TimetableGenerator}: it re-derives
 * every hard constraint by sweeping occupancy maps so a solver bug cannot publish a bad timetable,
 * and the same code backs the validation of hand-made edits.
 */
@Component
public class TimetableValidator {

    public record Issue(String kind, String severity, String message, UUID entryId, UUID otherEntryId) {
    }

    public record Report(List<Issue> issues, int hardCount, int softCount, double score, Instant generatedAt) {
    }

    private static final int MAX_DAILY_PERIODS = 8;

    /**
     * @param rules              period geometry and the consecutive-lesson rule
     * @param teacherWeeklyLimit membership id to maximum weekly periods (null limits skipped)
     */
    public Report validate(List<TimetableEntry> entries, TimetableRules rules,
                           Map<UUID, Integer> teacherWeeklyLimit) {
        List<Issue> issues = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TimetableEntry a = entries.get(i);
            if (rules.hasLayout() && !rules.isTeachingPeriod(a.getPeriodSeq())) {
                issues.add(new Issue("period_out_of_range", "hard",
                        "Period " + a.getPeriodSeq() + " is not a teaching period.", a.getId(), null));
            }
            if (a.getPeriodSeq() + a.getSpan() - 1 > 24) {
                issues.add(new Issue("period_out_of_range", "hard",
                        "The entry extends beyond the end of the day.", a.getId(), null));
            }
            if (a.getSpan() > 1 && rules.hasLayout() && rules.isTeachingPeriod(a.getPeriodSeq())
                    && !spansOneSession(a, rules)) {
                issues.add(new Issue("double_spans_break", "hard",
                        "A double period spans a break or lunch.", a.getId(), null));
            }
            for (int j = i + 1; j < entries.size(); j++) {
                TimetableEntry b = entries.get(j);
                if (!overlaps(a, b)) {
                    continue;
                }
                if (sameClass(a, b)) {
                    issues.add(new Issue("class_clash", "hard",
                            "The class has two lessons in the same slot.", a.getId(), b.getId()));
                }
                if (a.getTeacherMembershipId() != null
                        && a.getTeacherMembershipId().equals(b.getTeacherMembershipId())) {
                    issues.add(new Issue("teacher_clash", "hard",
                            "The teacher is double-booked in the same slot.", a.getId(), b.getId()));
                }
                if (a.getClassroomId() != null && a.getClassroomId().equals(b.getClassroomId())) {
                    issues.add(new Issue("room_clash", "hard",
                            "The room is double-booked in the same slot.", a.getId(), b.getId()));
                }
            }
        }
        issues.addAll(consecutiveIssues(entries, rules));
        issues.addAll(gapIssues(entries, rules));
        issues.addAll(workloadIssues(entries, teacherWeeklyLimit));
        int hard = (int) issues.stream().filter(i -> "hard".equals(i.severity())).count();
        int soft = issues.size() - hard;
        double score = Math.max(0, 100 - 15 * hard - 2 * soft);
        return new Report(issues, hard, soft, score, Instant.now());
    }

    /**
     * A teacher may not teach two separate lessons in adjacent periods; back-to-back periods are
     * only allowed as the two halves of one double-period entry (spec 12.1). Exposed separately so
     * manual edits and swaps can be checked without re-running the whole report.
     */
    public List<Issue> consecutiveIssues(List<TimetableEntry> entries, TimetableRules rules) {
        List<Issue> issues = new ArrayList<>();
        Map<UUID, Map<Integer, List<TimetableEntry>>> byTeacherAndDay = new LinkedHashMap<>();
        for (TimetableEntry entry : entries) {
            if (entry.getTeacherMembershipId() == null) {
                continue;
            }
            byTeacherAndDay
                    .computeIfAbsent(entry.getTeacherMembershipId(), key -> new TreeMap<>())
                    .computeIfAbsent(entry.getDayOfWeek(), key -> new ArrayList<>())
                    .add(entry);
        }
        for (Map.Entry<UUID, Map<Integer, List<TimetableEntry>>> teacher : byTeacherAndDay.entrySet()) {
            int limit = rules.maxConsecutiveFor(teacher.getKey());
            for (Map.Entry<Integer, List<TimetableEntry>> day : teacher.getValue().entrySet()) {
                for (List<TimetableEntry> run : runsForDay(day.getValue(), rules)) {
                    if (run.size() <= limit) {
                        continue;
                    }
                    TimetableEntry first = run.get(0);
                    TimetableEntry second = run.get(1);
                    issues.add(new Issue("teacher_consecutive", "hard",
                            "A teacher has " + run.size() + " lessons back to back on day "
                                    + first.getDayOfWeek() + " from period " + first.getPeriodSeq()
                                    + "; only " + limit + " is allowed without a double period.",
                            first.getId(), second.getId()));
                }
            }
        }
        return issues;
    }

    /**
     * Soft goal (spec 12.1): a teacher's day should not be broken up by long free stretches. A run
     * of more than two consecutive free periods between two lessons inside one session is reported
     * as guidance, never as a blocker, and a gap across a break or lunch is not counted at all.
     */
    public List<Issue> gapIssues(List<TimetableEntry> entries, TimetableRules rules) {
        List<Issue> issues = new ArrayList<>();
        if (!rules.hasLayout()) {
            return issues;
        }
        Map<UUID, Map<Integer, Set<Integer>>> occupied = new LinkedHashMap<>();
        for (TimetableEntry entry : entries) {
            if (entry.getTeacherMembershipId() == null) {
                continue;
            }
            Set<Integer> slots = occupied
                    .computeIfAbsent(entry.getTeacherMembershipId(), key -> new TreeMap<>())
                    .computeIfAbsent(entry.getDayOfWeek(), key -> new HashSet<>());
            for (int slot = entry.getPeriodSeq(); slot < entry.getPeriodSeq() + entry.getSpan(); slot++) {
                slots.add(slot);
            }
        }
        for (Map.Entry<UUID, Map<Integer, Set<Integer>>> teacher : occupied.entrySet()) {
            for (Map.Entry<Integer, Set<Integer>> day : teacher.getValue().entrySet()) {
                for (List<Integer> session : rules.sessions()) {
                    int previousOccupied = -1;
                    for (int index = 0; index < session.size(); index++) {
                        if (!day.getValue().contains(session.get(index))) {
                            continue;
                        }
                        int gap = index - previousOccupied - 1;
                        if (previousOccupied >= 0 && gap > TimetableRules.MAX_ACCEPTABLE_GAP_PERIODS) {
                            issues.add(new Issue("teacher_long_gap", "soft",
                                    "A teacher has a gap of " + gap + " free periods on day " + day.getKey()
                                            + " before period " + session.get(index) + ".", null, null));
                        }
                        previousOccupied = index;
                    }
                }
            }
        }
        return issues;
    }

    /**
     * Splits one teacher's day into runs of occupied slots that touch. A double period occupies two
     * adjacent slots but counts as a single lesson, which is what makes it legal.
     */
    private List<List<TimetableEntry>> runsForDay(List<TimetableEntry> dayEntries, TimetableRules rules) {
        TreeMap<Integer, TimetableEntry> slotOwner = new TreeMap<>();
        for (TimetableEntry entry : dayEntries) {
            for (int slot = entry.getPeriodSeq(); slot < entry.getPeriodSeq() + entry.getSpan(); slot++) {
                slotOwner.put(slot, entry);
            }
        }
        List<List<TimetableEntry>> runs = new ArrayList<>();
        List<TimetableEntry> current = null;
        Integer previousSlot = null;
        for (Map.Entry<Integer, TimetableEntry> slot : slotOwner.entrySet()) {
            TimetableEntry owner = slot.getValue();
            boolean continues = previousSlot != null && rules.inSameSession(previousSlot, slot.getKey());
            if (!continues) {
                current = new ArrayList<>();
                runs.add(current);
            }
            if (current != null && !current.contains(owner)) {
                current.add(owner);
            }
            previousSlot = slot.getKey();
        }
        return runs;
    }

    private boolean spansOneSession(TimetableEntry entry, TimetableRules rules) {
        for (int slot = entry.getPeriodSeq(); slot < entry.getPeriodSeq() + entry.getSpan(); slot++) {
            if (!rules.isTeachingPeriod(slot)) {
                return false;
            }
            if (slot > entry.getPeriodSeq() && !rules.inSameSession(slot - 1, slot)) {
                return false;
            }
        }
        return true;
    }

    private List<Issue> workloadIssues(List<TimetableEntry> entries, Map<UUID, Integer> teacherWeeklyLimit) {
        List<Issue> issues = new ArrayList<>();
        Map<UUID, Integer> weekly = new HashMap<>();
        Map<UUID, Map<Integer, Integer>> daily = new HashMap<>();
        for (TimetableEntry entry : entries) {
            UUID teacher = entry.getTeacherMembershipId();
            if (teacher == null) {
                continue;
            }
            weekly.merge(teacher, entry.getSpan(), Integer::sum);
            daily.computeIfAbsent(teacher, k -> new HashMap<>())
                    .merge(entry.getDayOfWeek(), entry.getSpan(), Integer::sum);
        }
        for (Map.Entry<UUID, Map<Integer, Integer>> byTeacher : daily.entrySet()) {
            for (Map.Entry<Integer, Integer> day : byTeacher.getValue().entrySet()) {
                if (day.getValue() > MAX_DAILY_PERIODS) {
                    issues.add(new Issue("teacher_daily_load", "soft",
                            "A teacher has " + day.getValue() + " periods on day " + day.getKey() + ".",
                            null, null));
                }
            }
        }
        if (teacherWeeklyLimit != null) {
            for (Map.Entry<UUID, Integer> weeklyLoad : weekly.entrySet()) {
                Integer limit = teacherWeeklyLimit.get(weeklyLoad.getKey());
                if (limit != null && weeklyLoad.getValue() > limit) {
                    issues.add(new Issue("teacher_weekly_load", "soft",
                            "A teacher has " + weeklyLoad.getValue() + " periods per week, above the limit of "
                                    + limit + ".", null, null));
                }
            }
        }
        return issues;
    }

    public static boolean overlaps(TimetableEntry a, TimetableEntry b) {
        return a.getDayOfWeek() == b.getDayOfWeek()
                && a.getPeriodSeq() < b.getPeriodSeq() + b.getSpan()
                && b.getPeriodSeq() < a.getPeriodSeq() + a.getSpan();
    }

    public static boolean sameClass(TimetableEntry a, TimetableEntry b) {
        return a.getClassGroupId().equals(b.getClassGroupId()) && Objects.equals(a.getStreamId(), b.getStreamId());
    }
}
