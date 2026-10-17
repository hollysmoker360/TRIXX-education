package com.schoolsphere.timetable.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expands a timetable version into dated lesson occurrences (spec 12.2). Deliberately free of Spring
 * and the database: matching weekdays, combining period times with the school's time zone, refusing
 * doubles that cross a break and bounding the window are all pure arithmetic, so they are unit
 * tested directly rather than through an integration test.
 */
public final class OccurrenceMaterialiser {

    /** The most days one call may materialise, so a bad request cannot write years of rows. */
    public static final int MAX_WINDOW_DAYS = 120;

    private OccurrenceMaterialiser() {
    }

    /** A teaching or break row from {@code timetable_period}. */
    public record PeriodSlot(int seq, LocalTime startsAt, LocalTime endsAt, boolean isBreak) {
    }

    /** The parts of a {@code timetable_entry} that the expansion needs. */
    public record EntrySlot(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                            UUID classroomId, int dayOfWeek, int periodSeq, int span) {
    }

    /** One dated lesson. A double period stays one occurrence with {@code span == 2}. */
    public record Occurrence(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                             UUID classroomId, LocalDate lessonDate, Instant startsAt, Instant endsAt,
                             int span) {
    }

    public static void requireValidWindow(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("The schedule window must end on or after it starts.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                    "Materialise at most " + MAX_WINDOW_DAYS + " days at a time.");
        }
    }

    public static List<Occurrence> expand(List<EntrySlot> entries, List<PeriodSlot> periods,
                                          LocalDate from, LocalDate to, ZoneId zone) {
        requireValidWindow(from, to);
        Map<Integer, PeriodSlot> bySeq = new HashMap<>();
        for (PeriodSlot period : periods) {
            bySeq.putIfAbsent(period.seq(), period);
        }
        List<Occurrence> occurrences = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            for (EntrySlot entry : entries) {
                if (entry.dayOfWeek() != weekday || spansBreak(bySeq, entry)) {
                    continue;
                }
                PeriodSlot first = bySeq.get(entry.periodSeq());
                PeriodSlot last = bySeq.get(entry.periodSeq() + Math.max(1, entry.span()) - 1);
                if (first == null || last == null) {
                    continue;
                }
                occurrences.add(new Occurrence(entry.classGroupId(), entry.streamId(), entry.subjectId(),
                        entry.teacherMembershipId(), entry.classroomId(), date,
                        ZonedDateTime.of(date, first.startsAt(), zone).toInstant(),
                        ZonedDateTime.of(date, last.endsAt(), zone).toInstant(),
                        Math.max(1, entry.span())));
            }
        }
        return occurrences;
    }

    /**
     * True when the entry covers a missing period or a break, which also rejects a double period that
     * would straddle assembly, break or lunch (spec 12.1).
     */
    public static boolean spansBreak(Map<Integer, PeriodSlot> bySeq, EntrySlot entry) {
        for (int seq = entry.periodSeq(); seq < entry.periodSeq() + Math.max(1, entry.span()); seq++) {
            PeriodSlot period = bySeq.get(seq);
            if (period == null || period.isBreak()) {
                return true;
            }
        }
        return false;
    }
}
