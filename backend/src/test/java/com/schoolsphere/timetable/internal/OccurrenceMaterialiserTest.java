package com.schoolsphere.timetable.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.schoolsphere.timetable.internal.OccurrenceMaterialiser.EntrySlot;
import com.schoolsphere.timetable.internal.OccurrenceMaterialiser.Occurrence;
import com.schoolsphere.timetable.internal.OccurrenceMaterialiser.PeriodSlot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The expansion from a timetable grid to dated lessons (spec 12.2): weekdays, period times in the
 * school's zone, doubles that must not cross a break, and the window bound.
 */
class OccurrenceMaterialiserTest {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final UUID CLASS = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();
    // 2026-10-19 is a Monday.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private static List<PeriodSlot> periods() {
        return List.of(
                new PeriodSlot(1, LocalTime.of(8, 0), LocalTime.of(8, 40), false),
                new PeriodSlot(2, LocalTime.of(8, 40), LocalTime.of(9, 20), false),
                new PeriodSlot(3, LocalTime.of(9, 20), LocalTime.of(10, 0), false),
                new PeriodSlot(4, LocalTime.of(10, 0), LocalTime.of(10, 30), true),
                new PeriodSlot(5, LocalTime.of(10, 30), LocalTime.of(11, 10), false));
    }

    private static EntrySlot entry(int day, int period, int span) {
        return new EntrySlot(CLASS, null, SUBJECT, UUID.randomUUID(), null, day, period, span);
    }

    private static List<Occurrence> expand(EntrySlot entry, LocalDate from, LocalDate to) {
        return OccurrenceMaterialiser.expand(List.of(entry), periods(), from, to, NAIROBI);
    }

    @Test
    void expandsOnlyOnTheMatchingWeekday() {
        List<Occurrence> occurrences = expand(entry(1, 1, 1), MONDAY, MONDAY.plusDays(3));

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).lessonDate()).isEqualTo(MONDAY);
        assertThat(occurrences.get(0).startsAt())
                .isEqualTo(ZonedDateTime.of(MONDAY, LocalTime.of(8, 0), NAIROBI).toInstant());
        assertThat(occurrences.get(0).endsAt())
                .isEqualTo(ZonedDateTime.of(MONDAY, LocalTime.of(8, 40), NAIROBI).toInstant());
    }

    @Test
    void aDoublePeriodStaysOneOccurrenceCoveringBothPeriods() {
        List<Occurrence> occurrences = expand(entry(1, 1, 2), MONDAY, MONDAY);

        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).span()).isEqualTo(2);
        assertThat(occurrences.get(0).endsAt())
                .isEqualTo(ZonedDateTime.of(MONDAY, LocalTime.of(9, 20), NAIROBI).toInstant());
    }

    @Test
    void aDoubleThatWouldCrossABreakIsNotScheduled() {
        assertThat(expand(entry(1, 3, 2), MONDAY, MONDAY)).isEmpty();
        assertThat(expand(entry(1, 4, 2), MONDAY, MONDAY)).isEmpty();
    }

    @Test
    void anEntryReferencingAnUnknownPeriodIsIgnored() {
        assertThat(expand(entry(1, 9, 1), MONDAY, MONDAY)).isEmpty();
    }

    @Test
    void theWindowIsBounded() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> expand(entry(1, 1, 1), MONDAY, MONDAY.plusDays(
                        OccurrenceMaterialiser.MAX_WINDOW_DAYS)));
    }

    @Test
    void aReversedWindowIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OccurrenceMaterialiser.requireValidWindow(MONDAY, MONDAY.minusDays(1)));
    }
}
