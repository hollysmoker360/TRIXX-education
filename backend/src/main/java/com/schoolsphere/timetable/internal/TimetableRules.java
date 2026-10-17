package com.schoolsphere.timetable.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Timetable geometry and school-configurable rules (spec 12.1).
 *
 * <p>The grid is a set of periods ordered by {@code seq}. A period flagged as a break is not
 * teachable and ends the session, so a double period can never span a break and two lessons on
 * opposite sides of a break (or lunch) are not adjacent. Two teaching periods are adjacent when
 * nothing sits between them in the ordered period list.
 *
 * <p>{@code maxConsecutivePeriods} bounds how many separate lessons a teacher may place back to
 * back. The default of 1 forbids two lessons in adjacent periods unless both halves belong to one
 * double-period entry; {@code teacherMaxConsecutivePeriods} relaxes that for named staff without a
 * code change. Values come from the school settings blob ({@code school.settings}) so no schema
 * change is needed.
 */
public record TimetableRules(Set<Integer> teachingPeriods, Set<Long> adjacentPairs, Set<Integer> teachingDays,
                             int maxConsecutivePeriods, Map<UUID, Integer> teacherMaxConsecutivePeriods) {

    public static final int DEFAULT_MAX_CONSECUTIVE_PERIODS = 1;
    /** A free run longer than this many periods inside one session is a gap worth avoiding. */
    public static final int MAX_ACCEPTABLE_GAP_PERIODS = 2;
    public static final Set<Integer> DEFAULT_TEACHING_DAYS = Set.of(1, 2, 3, 4, 5);
    public static final String SETTING_MAX_CONSECUTIVE = "timetable.maxConsecutivePeriods";
    public static final String SETTING_TEACHER_MAX_CONSECUTIVE = "timetable.teacherMaxConsecutivePeriods";
    public static final String SETTING_TEACHING_DAYS = "timetable.teachingDays";

    public TimetableRules {
        teachingPeriods = teachingPeriods == null ? Set.of() : Set.copyOf(teachingPeriods);
        adjacentPairs = adjacentPairs == null ? Set.of() : Set.copyOf(adjacentPairs);
        teachingDays = teachingDays == null || teachingDays.isEmpty() ? DEFAULT_TEACHING_DAYS : Set.copyOf(teachingDays);
        maxConsecutivePeriods = Math.max(1, maxConsecutivePeriods);
        teacherMaxConsecutivePeriods =
                teacherMaxConsecutivePeriods == null ? Map.of() : Map.copyOf(teacherMaxConsecutivePeriods);
    }

    /** A grid with no breaks: periods 1..{@code lastPeriod}, strict consecutive rule. */
    public static TimetableRules contiguous(int lastPeriod) {
        return contiguous(1, lastPeriod, DEFAULT_MAX_CONSECUTIVE_PERIODS);
    }

    /** A grid with no breaks: periods {@code firstPeriod}..{@code lastPeriod}, strict consecutive rule. */
    public static TimetableRules contiguous(int firstPeriod, int lastPeriod) {
        return contiguous(firstPeriod, lastPeriod, DEFAULT_MAX_CONSECUTIVE_PERIODS);
    }

    /** A grid with no breaks: periods {@code firstPeriod}..{@code lastPeriod}. */
    public static TimetableRules contiguous(int firstPeriod, int lastPeriod, int maxConsecutivePeriods) {
        Set<Integer> teaching = new LinkedHashSet<>();
        Set<Long> pairs = new LinkedHashSet<>();
        for (int seq = firstPeriod; seq <= lastPeriod; seq++) {
            teaching.add(seq);
            if (seq > firstPeriod) {
                pairs.add(pairKey(seq - 1, seq));
            }
        }
        return new TimetableRules(teaching, pairs, DEFAULT_TEACHING_DAYS, maxConsecutivePeriods, Map.of());
    }

    public TimetableRules withTeachingDays(Set<Integer> days) {
        return new TimetableRules(teachingPeriods, adjacentPairs, days, maxConsecutivePeriods,
                teacherMaxConsecutivePeriods);
    }

    public TimetableRules withMaxConsecutivePeriods(int value) {
        return new TimetableRules(teachingPeriods, adjacentPairs, teachingDays, value,
                teacherMaxConsecutivePeriods);
    }

    public TimetableRules withTeacherLimits(Map<UUID, Integer> limits) {
        return new TimetableRules(teachingPeriods, adjacentPairs, teachingDays, maxConsecutivePeriods, limits);
    }

    /** False when the school has not configured any periods, in which case geometry checks are skipped. */
    public boolean hasLayout() {
        return !teachingPeriods.isEmpty();
    }

    public boolean isTeachingPeriod(int seq) {
        return teachingPeriods.contains(seq);
    }

    /** True when nothing (no period and no break) sits between the two periods. */
    public boolean inSameSession(int firstPeriod, int secondPeriod) {
        return firstPeriod != secondPeriod && adjacentPairs.contains(pairKey(firstPeriod, secondPeriod));
    }

    /** The effective consecutive-lesson limit for one teacher: their override or the school default. */
    public int maxConsecutiveFor(UUID teacherMembershipId) {
        if (teacherMembershipId == null) {
            return maxConsecutivePeriods;
        }
        Integer override = teacherMaxConsecutivePeriods.get(teacherMembershipId);
        return override == null || override < 1 ? maxConsecutivePeriods : override;
    }

    /**
     * The periods of each session in order, where a session is a maximal run of adjacent teaching
     * periods. A break or lunch starts the next session, so this is the list a gap check walks.
     */
    public List<List<Integer>> sessions() {
        List<Integer> sorted = new ArrayList<>(teachingPeriods);
        Collections.sort(sorted);
        List<List<Integer>> sessions = new ArrayList<>();
        List<Integer> current = null;
        Integer previous = null;
        for (int seq : sorted) {
            if (current == null || previous == null || !inSameSession(previous, seq)) {
                current = new ArrayList<>();
                sessions.add(current);
            }
            current.add(seq);
            previous = seq;
        }
        return sessions;
    }

    /** Lengths of the maximal runs of adjacent teaching periods, i.e. the teaching sessions in a day. */
    public List<Integer> sessionLengths() {
        List<Integer> lengths = new ArrayList<>();
        for (List<Integer> session : sessions()) {
            lengths.add(session.size());
        }
        return lengths;
    }

    /**
     * Counts the free runs longer than {@link #MAX_ACCEPTABLE_GAP_PERIODS} for one teacher on one
     * day. Runs are measured inside a session, so a break or lunch never counts as a gap. Shared by
     * the solver's objective and the validator's guidance so both agree on what a gap is.
     */
    public static int countLongGaps(Set<Integer> occupiedSlots, List<List<Integer>> sessions) {
        int count = 0;
        for (List<Integer> session : sessions) {
            int previous = -1;
            for (int index = 0; index < session.size(); index++) {
                if (!occupiedSlots.contains(session.get(index))) {
                    continue;
                }
                if (previous >= 0 && index - previous - 1 > MAX_ACCEPTABLE_GAP_PERIODS) {
                    count++;
                }
                previous = index;
            }
        }
        return count;
    }

    /**
     * The most periods one teacher can teach in a week under this rule, assuming lessons may be
     * scheduled as double periods. This is the school-level bound used to answer "can this load be
     * placed at all?" before the solver runs. It assumes the strict rule, so it is a conservative
     * figure for a teacher whose override allows back-to-back lessons.
     */
    public int weeklyCapacityInPeriods() {
        if (!hasLayout()) {
            return 0;
        }
        int perDay = sessionLengths().stream().mapToInt(length -> maxPeriodsInSession(length, true)).sum();
        int days = teachingDays.isEmpty() ? DEFAULT_TEACHING_DAYS.size() : teachingDays.size();
        return perDay * days;
    }

    /**
     * The most periods that fit in a session of {@code length} adjacent slots when no two separate
     * lessons may touch. Each lesson covers one or two slots, and a free slot must separate two
     * lessons, so eight slots split as 4+4 hold 3 + 3 periods with doubles or 2 + 2 without.
     */
    public static int maxPeriodsInSession(int length, boolean doublesAllowed) {
        if (length <= 0) {
            return 0;
        }
        int[] best = new int[length + 1];
        for (int slot = 1; slot <= length; slot++) {
            int value = best[slot - 1];
            value = Math.max(value, at(best, slot - 2) + 1);
            if (doublesAllowed && slot >= 2) {
                value = Math.max(value, at(best, slot - 3) + 2);
            }
            best[slot] = value;
        }
        return best[length];
    }

    private static int at(int[] best, int index) {
        return index < 0 ? 0 : best[index];
    }

    public static long pairKey(int firstPeriod, int secondPeriod) {
        return (long) Math.min(firstPeriod, secondPeriod) * 1000L + Math.max(firstPeriod, secondPeriod);
    }
}
