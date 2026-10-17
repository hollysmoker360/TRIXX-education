package com.schoolsphere.timetable.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the reminders for a set of occurrences (spec 12.2): one per occurrence per recipient, due
 * a configurable number of minutes before the lesson starts. The due time and the idempotency key
 * are derived from the occurrence, the recipient and the offset, so re-running materialisation can
 * never create or send a duplicate, and a double lesson — one occurrence with span two — produces
 * exactly one reminder at the start of the double period.
 */
public final class LessonReminderPlanner {

    /** Notification type carried by lesson reminders. */
    public static final String TYPE = "lesson.reminder";
    /** Two minutes before the lesson, per the handbook (spec 12.2). */
    public static final int DEFAULT_LEAD_MINUTES = 2;

    private LessonReminderPlanner() {
    }

    /** The parts of an occurrence a reminder needs, after recipient resolution. */
    public record Occurrence(UUID occurrenceId, String className, String subjectName, Instant startsAt,
                             int span) {
    }

    public record Reminder(UUID occurrenceId, UUID userId, int offsetMinutes, Instant dueAt,
                           String title, String body, Map<String, Object> data, String dedupeKey) {
    }

    /**
     * @param recipients       user ids to remind, keyed by occurrence
     * @param leadOverrides    per-user lead time in minutes (spec 12.2 "with per-user override")
     * @param defaultLeadMinutes the school's default lead time
     */
    public static List<Reminder> plan(List<Occurrence> occurrences, Map<UUID, Set<UUID>> recipients,
                                      Map<UUID, Integer> leadOverrides, int defaultLeadMinutes) {
        List<Reminder> reminders = new ArrayList<>();
        for (Occurrence occurrence : occurrences) {
            for (UUID userId : recipients.getOrDefault(occurrence.occurrenceId(), Set.of())) {
                int lead = resolveLead(userId, leadOverrides, defaultLeadMinutes);
                reminders.add(new Reminder(occurrence.occurrenceId(), userId, lead,
                        occurrence.startsAt().minusSeconds(lead * 60L), title(occurrence),
                        body(occurrence, lead), data(occurrence, lead),
                        dedupeKey(occurrence.occurrenceId(), lead)));
            }
        }
        return reminders;
    }

    public static int resolveLead(UUID userId, Map<UUID, Integer> leadOverrides, int defaultLeadMinutes) {
        Integer override = leadOverrides == null ? null : leadOverrides.get(userId);
        return override == null ? Math.max(0, defaultLeadMinutes) : Math.max(0, override);
    }

    /** The idempotency key (occurrence, user, offset); the channel is appended by the outbox layer. */
    public static String dedupeKey(UUID occurrenceId, int offsetMinutes) {
        return "lesson.reminder:" + occurrenceId + ":" + offsetMinutes;
    }

    /** Prefix matching every reminder of one occurrence, whatever the offset or channel. */
    public static String dedupeKeyPrefix(UUID occurrenceId) {
        return "lesson.reminder:" + occurrenceId + ":";
    }

    private static String title(Occurrence occurrence) {
        return "Lesson reminder: " + occurrence.subjectName();
    }

    private static String body(Occurrence occurrence, int lead) {
        int minutes = Math.max(1, lead);
        StringBuilder body = new StringBuilder(occurrence.subjectName())
                .append(" for ").append(occurrence.className());
        if (occurrence.span() > 1) {
            body.append(" (double period)");
        }
        return body.append(" starts in ").append(minutes)
                .append(minutes == 1 ? " minute." : " minutes.").toString();
    }

    private static Map<String, Object> data(Occurrence occurrence, int lead) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("occurrenceId", occurrence.occurrenceId().toString());
        data.put("startsAt", occurrence.startsAt().toString());
        data.put("leadMinutes", lead);
        data.put("span", occurrence.span());
        return data;
    }
}
