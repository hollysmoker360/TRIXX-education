package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The capacity arithmetic behind the "can this load be placed?" pre-check. The numbers here are the
 * same ones a school administrator sees when the rule is explained to them: an eight-period day
 * split by lunch (4 + 4) holds 3 periods per session with doubles, or 2 without.
 */
class TimetableRulesTest {

    @Test
    void aSessionHoldsHalfItsPeriodsWhenOnlySinglePeriodsAreAllowed() {
        assertThat(TimetableRules.maxPeriodsInSession(1, false)).isEqualTo(1);
        assertThat(TimetableRules.maxPeriodsInSession(2, false)).isEqualTo(1);
        assertThat(TimetableRules.maxPeriodsInSession(4, false)).isEqualTo(2);
        assertThat(TimetableRules.maxPeriodsInSession(8, false)).isEqualTo(4);
    }

    @Test
    void doublingUpLetsASessionHoldMorePeriods() {
        assertThat(TimetableRules.maxPeriodsInSession(1, true)).isEqualTo(1);
        assertThat(TimetableRules.maxPeriodsInSession(2, true)).isEqualTo(2);
        assertThat(TimetableRules.maxPeriodsInSession(3, true)).isEqualTo(2);
        assertThat(TimetableRules.maxPeriodsInSession(4, true)).isEqualTo(3);
        assertThat(TimetableRules.maxPeriodsInSession(5, true)).isEqualTo(4);
        assertThat(TimetableRules.maxPeriodsInSession(8, true)).isEqualTo(6);
    }

    @Test
    void anEmptySessionHoldsNothing() {
        assertThat(TimetableRules.maxPeriodsInSession(0, true)).isZero();
        assertThat(TimetableRules.maxPeriodsInSession(-3, true)).isZero();
    }

    @Test
    void aBreakEndsTheSession() {
        // Periods 1-4, lunch, then 6-9: the double period may not reach across the lunch break.
        TimetableRules rules = rules(Set.of(1, 2, 3, 4, 6, 7, 8, 9),
                Set.of(pair(1, 2), pair(2, 3), pair(3, 4), pair(6, 7), pair(7, 8), pair(8, 9)));

        assertThat(rules.sessionLengths()).containsExactly(4, 4);
        assertThat(rules.inSameSession(4, 6)).isFalse();
        assertThat(rules.isTeachingPeriod(5)).isFalse();
    }

    @Test
    void weeklyCapacityMatchesTheWorkedExample() {
        TimetableRules rules = rules(Set.of(1, 2, 3, 4, 6, 7, 8, 9),
                Set.of(pair(1, 2), pair(2, 3), pair(3, 4), pair(6, 7), pair(7, 8), pair(8, 9)));

        // 3 periods per session x 2 sessions a day x 5 days.
        assertThat(rules.weeklyCapacityInPeriods()).isEqualTo(30);
        assertThat(rules.withTeachingDays(Set.of(1, 2, 3, 4, 5, 6)).weeklyCapacityInPeriods()).isEqualTo(36);
    }

    @Test
    void sessionsAreSplitByBreaks() {
        TimetableRules rules = rules(Set.of(1, 2, 3, 5, 6, 7),
                Set.of(pair(1, 2), pair(2, 3), pair(5, 6), pair(6, 7)));

        assertThat(rules.sessions()).containsExactly(List.of(1, 2, 3), List.of(5, 6, 7));
    }

    @Test
    void anUnconfiguredGridHasNoCapacityToReport() {
        TimetableRules rules = new TimetableRules(Set.of(), Set.of(), null, 1, Map.of());

        assertThat(rules.hasLayout()).isFalse();
        assertThat(rules.weeklyCapacityInPeriods()).isZero();
    }

    @Test
    void teacherOverridesFallBackToTheSchoolDefault() {
        TimetableRules rules = TimetableRules.contiguous(1, 8).withMaxConsecutivePeriods(1);
        java.util.UUID relaxedTeacher = java.util.UUID.randomUUID();
        TimetableRules withOverride = rules.withTeacherLimits(Map.of(relaxedTeacher, 3));

        assertThat(rules.maxConsecutiveFor(relaxedTeacher)).isEqualTo(1);
        assertThat(withOverride.maxConsecutiveFor(relaxedTeacher)).isEqualTo(3);
        assertThat(withOverride.maxConsecutiveFor(java.util.UUID.randomUUID())).isEqualTo(1);
        assertThat(withOverride.maxConsecutiveFor(null)).isEqualTo(1);
    }

    @Test
    void aLimitBelowOneIsClampedRatherThanDisablingTheRule() {
        TimetableRules rules = new TimetableRules(Set.of(1, 2), Set.of(pair(1, 2)), null, 0, Map.of());

        assertThat(rules.maxConsecutivePeriods()).isEqualTo(1);
        assertThat(rules.withTeacherLimits(Map.of(java.util.UUID.randomUUID(), 0)).maxConsecutivePeriods())
                .isEqualTo(1);
    }

    @Test
    void contiguousBuildsEveryAdjacentPair() {
        TimetableRules rules = TimetableRules.contiguous(1, 4);

        assertThat(rules.sessionLengths()).containsExactly(4);
        assertThat(rules.inSameSession(1, 2)).isTrue();
        assertThat(rules.inSameSession(2, 3)).isTrue();
        assertThat(rules.inSameSession(3, 4)).isTrue();
        assertThat(rules.inSameSession(1, 3)).isFalse();
        assertThat(rules.inSameSession(2, 2)).isFalse();
    }

    @Test
    void adjacencyIsSymmetricAndOrderIndependent() {
        assertThat(TimetableRules.pairKey(3, 4)).isEqualTo(TimetableRules.pairKey(4, 3));
    }

    private static TimetableRules rules(Set<Integer> teaching, Set<Long> pairs) {
        return new TimetableRules(teaching, pairs, null, 1, Map.of());
    }

    private static long pair(int first, int second) {
        return TimetableRules.pairKey(first, second);
    }
}
