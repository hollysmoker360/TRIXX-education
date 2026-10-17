package com.schoolsphere.timetable.internal;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.notifications.api.NotificationsApi;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.TenantsApi;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Rolls a published timetable into dated lesson occurrences and schedules the two-minute reminders
 * (spec 12.2). Occurrences are the only place a lesson exists "on a date", so they are also what a
 * teacher's or class's day view reads.
 *
 * <p>Materialisation is idempotent: an occurrence is identified by class, stream, subject, date and
 * start, and each reminder carries the idempotency key (occurrence, recipient, offset), so running
 * the same window twice refreshes the rows and creates no duplicate reminder.
 */
@Service
@TenantScoped
public class LessonScheduleService {

    /** School default, in minutes before a lesson, for the reminder. */
    public static final String SETTING_REMINDER_LEAD = "timetable.reminderLeadMinutes";
    /** Per-user override map (user id to minutes), following the teacher-override convention. */
    public static final String SETTING_USER_REMINDER_LEAD = "timetable.userReminderLeadMinutes";
    /** Whether guardians are reminded too; off by default (spec 12.2). */
    public static final String SETTING_REMINDER_GUARDIANS = "timetable.reminderNotifyGuardians";
    /** How many days ahead occurrences are kept materialised. */
    public static final String SETTING_HORIZON_DAYS = "timetable.scheduleHorizonDays";
    public static final int DEFAULT_HORIZON_DAYS = 30;

    private static final ZoneId FALLBACK_ZONE = ZoneId.of("Africa/Nairobi");
    private static final UUID NO_STREAM = new UUID(0L, 0L);

    private final TimetableVersionRepository versionRepository;
    private final PeopleApi peopleApi;
    private final TenantsApi tenantsApi;
    private final NotificationsApi notificationsApi;
    private final JdbcTemplate jdbc;

    public LessonScheduleService(TimetableVersionRepository versionRepository, PeopleApi peopleApi,
                                 TenantsApi tenantsApi, NotificationsApi notificationsApi,
                                 JdbcTemplate jdbc) {
        this.versionRepository = versionRepository;
        this.peopleApi = peopleApi;
        this.tenantsApi = tenantsApi;
        this.notificationsApi = notificationsApi;
        this.jdbc = jdbc;
    }

    // ----- materialisation -----

    /** Materialises an explicit window, for a member of staff filling in a future term. */
    public int materialise(UUID versionId, LocalDate from, LocalDate to) {
        UUID schoolId = TenantContext.schoolId();
        TimetableVersion version = requireVersion(versionId);
        return materialiseWindow(schoolId, version, from, to);
    }

    /**
     * Called when a version is published: the term's future occurrences are rebuilt from it, the
     * occurrences it replaced are cancelled (with their pending reminders), and the next {@code
     * scheduleHorizonDays} days are scheduled.
     */
    public int publishSchedule(UUID versionId) {
        UUID schoolId = TenantContext.schoolId();
        TimetableVersion version = requireVersion(versionId);
        cancelReplaced(schoolId, version);
        return rollForward(schoolId, version);
    }

    /** Extends one version's schedule to the horizon, staying inside the term's dates. */
    public int rollForward(UUID versionId) {
        UUID schoolId = TenantContext.schoolId();
        return rollForward(schoolId, requireVersion(versionId));
    }

    /** Extends every published version of the current tenant to the horizon. */
    public int rollForwardAllPublished() {
        UUID schoolId = TenantContext.schoolId();
        List<UUID> published = jdbc.queryForList("SELECT id FROM timetable_version WHERE school_id = ? "
                + "AND state = 'published' AND deleted_at IS NULL", UUID.class, schoolId);
        int total = 0;
        for (UUID versionId : published) {
            total += rollForward(schoolId, requireVersion(versionId));
        }
        return total;
    }

    private int rollForward(UUID schoolId, TimetableVersion version) {
        LocalDate today = LocalDate.now(schoolZone(schoolId));
        int horizon = horizonDays(schoolId);
        LocalDate from = today;
        LocalDate to = today.plusDays(horizon);
        LocalDate[] term = termDates(schoolId, version.getTermId());
        if (term == null) {
            return 0;
        }
        if (from.isBefore(term[0])) {
            from = term[0];
        }
        if (to.isAfter(term[1])) {
            to = term[1];
        }
        if (from.isAfter(to)) {
            return 0;
        }
        return materialiseWindow(schoolId, version, from, to);
    }

    private int materialiseWindow(UUID schoolId, TimetableVersion version, LocalDate from, LocalDate to) {
        OccurrenceMaterialiser.requireValidWindow(from, to);
        List<OccurrenceMaterialiser.EntrySlot> entries = entrySlots(schoolId, version.getId());
        List<OccurrenceMaterialiser.PeriodSlot> periods = periodSlots(schoolId, version.getAcademicYearId());
        Map<UUID, String> classNames = names("class_group", schoolId);
        Map<UUID, String> streamNames = names("stream", schoolId);
        Map<UUID, String> subjectNames = names("subject", schoolId);
        Instant now = Instant.now();
        int stored = 0;
        for (OccurrenceMaterialiser.Occurrence occurrence : OccurrenceMaterialiser.expand(entries, periods,
                from, to, schoolZone(schoolId))) {
            UUID occurrenceId = upsert(schoolId, version, occurrence);
            stored++;
            if (!occurrence.startsAt().isAfter(now)) {
                continue; // A lesson already under way is not worth reminding anyone about.
            }
            List<LessonReminderPlanner.Occurrence> planned = List.of(new LessonReminderPlanner.Occurrence(
                    occurrenceId,
                    classLabel(classNames, streamNames, occurrence.classGroupId(), occurrence.streamId()),
                    subjectNames.getOrDefault(occurrence.subjectId(), "Lesson"), occurrence.startsAt(),
                    occurrence.span()));
            scheduleReminders(schoolId,
                    planned, Map.of(occurrenceId, recipientUserIds(schoolId, occurrence)), now);
        }
        return stored;
    }

    private UUID upsert(UUID schoolId, TimetableVersion version,
                        OccurrenceMaterialiser.Occurrence occurrence) {
        UUID stream = occurrence.streamId() == null ? NO_STREAM : occurrence.streamId();
        List<UUID> existing = jdbc.query("SELECT id FROM lesson_occurrence WHERE school_id = ? "
                        + "AND class_group_id = ? AND subject_id = ? AND lesson_date = ? AND starts_at = ? "
                        + "AND coalesce(stream_id, ?) = ? AND deleted_at IS NULL",
                (rs, i) -> rs.getObject("id", UUID.class), schoolId, occurrence.classGroupId(),
                occurrence.subjectId(), occurrence.lessonDate(), timestamp(occurrence.startsAt()), NO_STREAM,
                stream);
        if (!existing.isEmpty()) {
            UUID id = existing.get(0);
            // Refresh in place and revive a cancelled row, so a re-published timetable re-uses the
            // same occurrence and its reminders stay unique (spec 12.2).
            jdbc.update("UPDATE lesson_occurrence SET timetable_version_id = ?, teacher_membership_id = ?, "
                            + "classroom_id = ?, ends_at = ?, span = ?, state = 'scheduled', "
                            + "cancelled_at = NULL, updated_at = now(), version = version + 1 "
                            + "WHERE school_id = ? AND id = ?",
                    version.getId(), occurrence.teacherMembershipId(), occurrence.classroomId(),
                    timestamp(occurrence.endsAt()), occurrence.span(), schoolId, id);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lesson_occurrence (id, school_id, timetable_version_id, term_id, "
                        + "class_group_id, stream_id, subject_id, teacher_membership_id, classroom_id, "
                        + "lesson_date, starts_at, ends_at, span, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'scheduled')",
                id, schoolId, version.getId(), version.getTermId(), occurrence.classGroupId(),
                occurrence.streamId(), occurrence.subjectId(), occurrence.teacherMembershipId(),
                occurrence.classroomId(), occurrence.lessonDate(), timestamp(occurrence.startsAt()),
                timestamp(occurrence.endsAt()), occurrence.span());
        return id;
    }

    private int cancelReplaced(UUID schoolId, TimetableVersion version) {
        List<UUID> cancelled = jdbc.query("UPDATE lesson_occurrence SET state = 'cancelled', "
                        + "cancelled_at = now(), updated_at = now(), version = version + 1 "
                        + "WHERE school_id = ? AND term_id = ? AND timetable_version_id <> ? "
                        + "AND state = 'scheduled' AND starts_at > now() AND deleted_at IS NULL "
                        + "RETURNING id",
                (rs, i) -> rs.getObject("id", UUID.class), schoolId, version.getTermId(), version.getId());
        for (UUID occurrenceId : cancelled) {
            notificationsApi.cancelScheduled(LessonReminderPlanner.dedupeKeyPrefix(occurrenceId));
        }
        return cancelled.size();
    }

    // ----- reminders -----

    private void scheduleReminders(UUID schoolId, List<LessonReminderPlanner.Occurrence> occurrences,
                                   Map<UUID, Set<UUID>> recipients, Instant now) {
        if (occurrences.isEmpty()) {
            return;
        }
        Map<String, Object> settings = tenantsApi.schoolSettings(schoolId);
        int defaultLead = intValue(settings.get(SETTING_REMINDER_LEAD),
                LessonReminderPlanner.DEFAULT_LEAD_MINUTES, 0);
        Map<UUID, Integer> overrides = userLeadOverrides(settings);
        for (LessonReminderPlanner.Reminder reminder
                : LessonReminderPlanner.plan(occurrences, recipients, overrides, defaultLead)) {
            Instant dueAt = reminder.dueAt().isBefore(now) ? now : reminder.dueAt();
            notificationsApi.scheduleNotifications(List.of(reminder.userId()), LessonReminderPlanner.TYPE,
                    reminder.title(), reminder.body(), reminder.data(), List.of("in_app"), dueAt,
                    reminder.dedupeKey());
        }
    }

    /** Teacher first, then the class's students; guardians only when the school opts in. */
    private Set<UUID> recipientUserIds(UUID schoolId, OccurrenceMaterialiser.Occurrence occurrence) {
        Set<UUID> users = new LinkedHashSet<>();
        if (occurrence.teacherMembershipId() != null) {
            peopleApi.teacherForMembership(schoolId, occurrence.teacherMembershipId())
                    .map(PeopleApi.TeacherRef::userId).filter(Objects::nonNull).ifPresent(users::add);
        }
        boolean remindGuardians = guardiansEnabled(schoolId);
        List<PeopleApi.StudentRef> students =
                peopleApi.listStudentsByClass(schoolId, occurrence.classGroupId(), occurrence.streamId());
        for (PeopleApi.StudentRef student : students) {
            if (student.userId() != null) {
                users.add(student.userId());
            }
            if (remindGuardians) {
                for (PeopleApi.ParentRef parent : peopleApi.guardiansOfStudent(schoolId, student.id())) {
                    if (parent.userId() != null) {
                        users.add(parent.userId());
                    }
                }
            }
        }
        return users;
    }

    // ----- read -----

    public List<OccurrenceView> list(UUID classGroupId, UUID teacherMembershipId, LocalDate from,
                                     LocalDate to) {
        UUID schoolId = TenantContext.schoolId();
        LocalDate start = from == null ? LocalDate.now(schoolZone(schoolId)) : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        StringBuilder sql = new StringBuilder("SELECT id, class_group_id, stream_id, subject_id, "
                + "teacher_membership_id, classroom_id, lesson_date, starts_at, ends_at, span, state "
                + "FROM lesson_occurrence WHERE school_id = ? AND deleted_at IS NULL AND state = 'scheduled' "
                + "AND lesson_date BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(schoolId, start, end));
        if (classGroupId != null) {
            sql.append(" AND class_group_id = ?");
            args.add(classGroupId);
        }
        if (teacherMembershipId != null) {
            sql.append(" AND teacher_membership_id = ?");
            args.add(teacherMembershipId);
        }
        sql.append(" ORDER BY starts_at");
        return jdbc.query(sql.toString(), (rs, i) -> new OccurrenceView(
                rs.getObject("id", UUID.class),
                rs.getObject("class_group_id", UUID.class),
                rs.getObject("stream_id", UUID.class),
                rs.getObject("subject_id", UUID.class),
                rs.getObject("teacher_membership_id", UUID.class),
                rs.getObject("classroom_id", UUID.class),
                rs.getObject("lesson_date", LocalDate.class),
                rs.getObject("starts_at", OffsetDateTime.class).toInstant(),
                rs.getObject("ends_at", OffsetDateTime.class).toInstant(),
                rs.getInt("span"),
                rs.getString("state")), args.toArray());
    }

    // ----- helpers -----

    private TimetableVersion requireVersion(UUID id) {
        return versionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(TenantContext.schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("timetable_version_not_found",
                        "Timetable version not found."));
    }

    private List<OccurrenceMaterialiser.EntrySlot> entrySlots(UUID schoolId, UUID versionId) {
        return jdbc.query("SELECT class_group_id, stream_id, subject_id, teacher_membership_id, "
                        + "classroom_id, day_of_week, period_seq, span FROM timetable_entry "
                        + "WHERE school_id = ? AND version_id = ? AND deleted_at IS NULL",
                (rs, i) -> new OccurrenceMaterialiser.EntrySlot(
                        rs.getObject("class_group_id", UUID.class),
                        rs.getObject("stream_id", UUID.class),
                        rs.getObject("subject_id", UUID.class),
                        rs.getObject("teacher_membership_id", UUID.class),
                        rs.getObject("classroom_id", UUID.class),
                        rs.getInt("day_of_week"),
                        rs.getInt("period_seq"),
                        rs.getInt("span")), schoolId, versionId);
    }

    private List<OccurrenceMaterialiser.PeriodSlot> periodSlots(UUID schoolId, UUID academicYearId) {
        return jdbc.query("SELECT seq, starts_at, ends_at, is_break FROM timetable_period "
                        + "WHERE school_id = ? AND (academic_year_id = ? OR academic_year_id IS NULL) "
                        + "AND deleted_at IS NULL ORDER BY seq, (academic_year_id IS NULL)",
                (rs, i) -> new OccurrenceMaterialiser.PeriodSlot(rs.getInt("seq"),
                        rs.getObject("starts_at", LocalTime.class),
                        rs.getObject("ends_at", LocalTime.class),
                        rs.getBoolean("is_break")), schoolId, academicYearId);
    }

    private ZoneId schoolZone(UUID schoolId) {
        String timezone = jdbc.query("SELECT timezone FROM school WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, schoolId);
        try {
            return timezone == null || timezone.isBlank() ? FALLBACK_ZONE : ZoneId.of(timezone);
        } catch (Exception e) {
            return FALLBACK_ZONE;
        }
    }

    private LocalDate[] termDates(UUID schoolId, UUID termId) {
        return jdbc.query("SELECT starts_on, ends_on FROM term WHERE school_id = ? AND id = ? "
                        + "AND deleted_at IS NULL",
                rs -> rs.next() ? new LocalDate[]{rs.getObject("starts_on", LocalDate.class),
                        rs.getObject("ends_on", LocalDate.class)} : null, schoolId, termId);
    }

    private Map<UUID, String> names(String table, UUID schoolId) {
        return jdbc.query("SELECT id, name FROM " + table + " WHERE school_id = ? AND deleted_at IS NULL",
                rs -> {
                    Map<UUID, String> values = new HashMap<>();
                    while (rs.next()) {
                        values.put(rs.getObject("id", UUID.class), rs.getString("name"));
                    }
                    return values;
                }, schoolId);
    }

    private String classLabel(Map<UUID, String> classNames, Map<UUID, String> streamNames,
                              UUID classGroupId, UUID streamId) {
        String name = classNames.getOrDefault(classGroupId, "the class");
        String stream = streamId == null ? null : streamNames.get(streamId);
        return stream == null ? name : name + " " + stream;
    }

    private int horizonDays(UUID schoolId) {
        return intValue(tenantsApi.schoolSettings(schoolId).get(SETTING_HORIZON_DAYS),
                DEFAULT_HORIZON_DAYS, 1);
    }

    private boolean guardiansEnabled(UUID schoolId) {
        return Boolean.TRUE.equals(tenantsApi.schoolSettings(schoolId).get(SETTING_REMINDER_GUARDIANS));
    }

    private Map<UUID, Integer> userLeadOverrides(Map<String, Object> settings) {
        Object value = settings.get(SETTING_USER_REMINDER_LEAD);
        if (!(value instanceof Map<?, ?> entries)) {
            return Map.of();
        }
        Map<UUID, Integer> overrides = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getValue() instanceof Number number) || number.intValue() < 0) {
                continue;
            }
            try {
                overrides.put(UUID.fromString(String.valueOf(entry.getKey())), number.intValue());
            } catch (IllegalArgumentException ignored) {
                // A malformed key is not a reason to fail scheduling; the school default applies.
            }
        }
        return overrides;
    }

    private int intValue(Object value, int fallback, int minimum) {
        return value instanceof Number number ? Math.max(minimum, number.intValue()) : fallback;
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record OccurrenceView(UUID id, UUID classGroupId, UUID streamId, UUID subjectId,
                                 UUID teacherMembershipId, UUID classroomId, LocalDate lessonDate,
                                 Instant startsAt, Instant endsAt, int span, String state) {
    }
}
