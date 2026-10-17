package com.schoolsphere.attendance.internal;

import com.schoolsphere.attendance.api.AttendanceApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.notifications.api.NotificationsApi;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import com.schoolsphere.tenants.api.TenantsApi;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Attendance (spec 11.6): sessions, append-only events with corrections as new events, and
 * reports. Attendance capture is in the offline subset; the sync engine applies captured events
 * through {@link #applyEvent}.
 */
@Service
@TenantScoped
public class AttendanceService implements AttendanceApi {

    private static final Set<String> STATUSES = Set.of("present", "absent", "late", "excused");

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceEventRepository eventRepository;
    private final PeopleApi peopleApi;
    private final TenantsApi tenantsApi;
    private final NotificationsApi notificationsApi;
    private final boolean absenceAlertsEnabled;
    private final String absenceAlertChannels;
    private final ZoneId zone;

    public AttendanceService(AttendanceSessionRepository sessionRepository,
                             AttendanceEventRepository eventRepository,
                             PeopleApi peopleApi,
                             TenantsApi tenantsApi,
                             NotificationsApi notificationsApi,
                             @Value("${schoolsphere.attendance.absence-alerts:true}") boolean absenceAlertsEnabled,
                             @Value("${schoolsphere.attendance.absence-alert-channels:in_app,sms}")
                             String absenceAlertChannels,
                             @Value("${schoolsphere.timezone:Africa/Nairobi}") String timezone) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.peopleApi = peopleApi;
        this.tenantsApi = tenantsApi;
        this.notificationsApi = notificationsApi;
        this.absenceAlertsEnabled = absenceAlertsEnabled;
        this.absenceAlertChannels = absenceAlertChannels;
        this.zone = ZoneId.of(timezone);
    }

    // ----- sessions -----

    public SessionView openSession(UUID classGroupId, UUID streamId, UUID subjectId, String sessionType,
                                   LocalDate sessionDate, Integer periodSeq, String notes) {
        UUID schoolId = schoolId();
        if (classGroupId == null) {
            throw ApiException.badRequest("class_required", "A class is required.");
        }
        String type = sessionType == null || sessionType.isBlank() ? "daily" : sessionType;
        if (!"daily".equals(type) && !"lesson".equals(type)) {
            throw ApiException.badRequest("invalid_session_type", "Unknown session type.");
        }
        if ("lesson".equals(type) && subjectId == null) {
            throw ApiException.badRequest("subject_required", "Lesson sessions require a subject.");
        }
        LocalDate date = sessionDate == null ? LocalDate.now(zone) : sessionDate;
        var existing = sessionRepository
                .findBySchoolIdAndClassGroupIdAndSessionDateAndSessionTypeAndStatusAndDeletedAtIsNull(
                        schoolId, classGroupId, date, type, "open").stream()
                .filter(s -> java.util.Objects.equals(s.getStreamId(), streamId)
                        && java.util.Objects.equals(s.getSubjectId(), subjectId)
                        && java.util.Objects.equals(s.getPeriodSeq(), periodSeq))
                .findFirst();
        if (existing.isPresent()) {
            return toSessionView(existing.get());
        }
        AttendanceSession session = new AttendanceSession();
        session.setSchoolId(schoolId);
        session.setClassGroupId(classGroupId);
        session.setStreamId(streamId);
        session.setSubjectId(subjectId);
        session.setTeacherMembershipId(TenantContext.actorMembershipId());
        session.setSessionType(type);
        session.setSessionDate(date);
        session.setPeriodSeq(periodSeq);
        session.setNotes(notes);
        session.setCreatedBy(TenantContext.actorUserId());
        return toSessionView(sessionRepository.save(session));
    }

    public List<SessionView> listSessions(LocalDate date, UUID classGroupId) {
        UUID schoolId = schoolId();
        List<AttendanceSession> sessions;
        if (classGroupId != null && date != null) {
            sessions = sessionRepository.findBySchoolIdAndClassGroupIdAndSessionDateAndDeletedAtIsNull(
                    schoolId, classGroupId, date);
        } else if (date != null) {
            sessions = sessionRepository.findBySchoolIdAndSessionDateAndDeletedAtIsNull(schoolId, date);
        } else if (classGroupId != null) {
            sessions = sessionRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNullOrderBySessionDateDesc(
                    schoolId, classGroupId);
        } else {
            sessions = List.of();
        }
        return sessions.stream().map(this::toSessionView).toList();
    }

    public SessionDetailView sessionDetail(UUID sessionId) {
        AttendanceSession session = requireSession(sessionId);
        List<PeopleApi.StudentRef> roster = peopleApi.listStudentsByClass(schoolId(), session.getClassGroupId(),
                session.getStreamId());
        Map<UUID, AttendanceEvent> latest = new HashMap<>();
        for (AttendanceEvent event : eventRepository.findLatestPerStudent(sessionId, schoolId())) {
            latest.put(event.getStudentId(), event);
        }
        List<RosterRow> rows = new ArrayList<>();
        int present = 0;
        int absent = 0;
        int late = 0;
        int excused = 0;
        int unmarked = 0;
        for (PeopleApi.StudentRef student : roster) {
            AttendanceEvent event = latest.get(student.id());
            String status = event == null ? null : event.getStatus();
            switch (status == null ? "" : status) {
                case "present" -> present++;
                case "absent" -> absent++;
                case "late" -> late++;
                case "excused" -> excused++;
                default -> unmarked++;
            }
            rows.add(new RosterRow(student.id(), student.fullName(), student.admissionNumber(), status,
                    event == null ? null : event.getReason(),
                    event == null ? null : event.getId(),
                    event == null ? null : event.getEventTime()));
        }
        return new SessionDetailView(toSessionView(session), rows,
                new AttendanceCounts(present, absent, late, excused, unmarked));
    }

    public SessionView closeSession(UUID sessionId, String notes) {
        AttendanceSession session = requireSession(sessionId);
        session.setStatus("closed");
        session.setClosedAt(Instant.now());
        if (notes != null && !notes.isBlank()) {
            session.setNotes(notes);
        }
        session.setUpdatedBy(TenantContext.actorUserId());
        return toSessionView(sessionRepository.save(session));
    }

    public void deleteSession(UUID sessionId) {
        AttendanceSession session = requireSession(sessionId);
        session.setDeletedAt(Instant.now());
        session.setUpdatedBy(TenantContext.actorUserId());
        sessionRepository.save(session);
    }

    // ----- marking -----

    public SessionDetailView mark(UUID sessionId, List<MarkInput> marks) {
        AttendanceSession session = requireSession(sessionId);
        if (!"open".equals(session.getStatus())) {
            throw ApiException.conflict("session_closed",
                    "This session is closed. Use a correction instead.");
        }
        if (marks == null || marks.isEmpty()) {
            throw ApiException.badRequest("marks_required", "At least one student must be marked.");
        }
        List<PeopleApi.StudentRef> roster = peopleApi.listStudentsByClass(schoolId(), session.getClassGroupId(),
                session.getStreamId());
        Map<UUID, PeopleApi.StudentRef> rosterById = new HashMap<>();
        roster.forEach(s -> rosterById.put(s.id(), s));
        List<UUID> absentStudentIds = new ArrayList<>();
        for (MarkInput mark : marks) {
            if (!STATUSES.contains(mark.status())) {
                throw ApiException.badRequest("invalid_status", "Unknown attendance status: " + mark.status());
            }
            if (!rosterById.containsKey(mark.studentId())) {
                throw ApiException.badRequest("student_not_in_class",
                        "The student is not enrolled in this class.");
            }
            appendEvent(sessionId, mark.studentId(), mark.status(), mark.reason(), null, Instant.now(), null);
            if ("absent".equals(mark.status())) {
                absentStudentIds.add(mark.studentId());
            }
        }
        if (absenceAlertsEnabled && !absentStudentIds.isEmpty()) {
            sendAbsenceAlerts(session, absentStudentIds, rosterById);
        }
        return sessionDetail(sessionId);
    }

    public EventView correct(UUID eventId, String status, String reason) {
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("invalid_status", "Unknown attendance status: " + status);
        }
        AttendanceEvent original = eventRepository.findBySchoolIdAndId(schoolId(), eventId)
                .orElseThrow(() -> ApiException.notFound("event_not_found", "Attendance event not found."));
        AttendanceEvent correction = appendEvent(original.getSessionId(), original.getStudentId(), status, reason,
                original.getId(), Instant.now(), null);
        return toEventView(correction);
    }

    @Override
    public EventOutcome applyEvent(EventInput input) {
        if (input.id() != null) {
            var existing = eventRepository.findBySchoolIdAndId(schoolId(), input.id());
            if (existing.isPresent()) {
                return new EventOutcome(toEventView(existing.get()), false);
            }
        }
        if (!STATUSES.contains(input.status())) {
            throw ApiException.badRequest("invalid_status", "Unknown attendance status: " + input.status());
        }
        AttendanceSession session = requireSession(input.sessionId());
        if (input.correctsEventId() != null && !hasPermission(PermissionCatalog.ATTENDANCE_CORRECT)) {
            throw ApiException.forbidden("correction_forbidden", "You may not record attendance corrections.");
        }
        if (input.correctsEventId() == null && !hasPermission(PermissionCatalog.ATTENDANCE_TAKE)
                && !hasPermission(PermissionCatalog.ATTENDANCE_CORRECT)) {
            throw ApiException.forbidden("attendance_forbidden", "You may not record attendance.");
        }
        AttendanceEvent event = new AttendanceEvent();
        event.setId(input.id());
        event.setSchoolId(schoolId());
        event.setSessionId(session.getId());
        event.setStudentId(input.studentId());
        event.setStatus(input.status());
        event.setReason(input.reason());
        event.setEventTime(input.eventTime());
        event.setOriginDeviceId(input.originDeviceId());
        event.setCorrectsEventId(input.correctsEventId());
        event.setRecordedByMembershipId(TenantContext.actorMembershipId());
        event = eventRepository.save(event);
        return new EventOutcome(toEventView(event), true);
    }

    private AttendanceEvent appendEvent(UUID sessionId, UUID studentId, String status, String reason,
                                        UUID correctsEventId, Instant eventTime, String originDeviceId) {
        AttendanceEvent event = new AttendanceEvent();
        event.setSchoolId(schoolId());
        event.setSessionId(sessionId);
        event.setStudentId(studentId);
        event.setStatus(status);
        event.setReason(reason);
        event.setEventTime(eventTime);
        event.setCorrectsEventId(correctsEventId);
        event.setOriginDeviceId(originDeviceId);
        event.setRecordedByMembershipId(TenantContext.actorMembershipId());
        return eventRepository.save(event);
    }

    private void sendAbsenceAlerts(AttendanceSession session, List<UUID> absentStudentIds,
                                   Map<UUID, PeopleApi.StudentRef> rosterById) {
        UUID schoolId = schoolId();
        Set<UUID> guardianUserIds = new LinkedHashSet<>();
        List<String> names = new ArrayList<>();
        for (UUID studentId : absentStudentIds) {
            PeopleApi.StudentRef student = rosterById.get(studentId);
            if (student != null) {
                names.add(student.fullName());
            }
            for (PeopleApi.ParentRef guardian : peopleApi.guardiansOfStudent(schoolId, studentId)) {
                guardianUserIds.add(guardian.userId());
            }
        }
        if (guardianUserIds.isEmpty()) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId().toString());
        data.put("date", session.getSessionDate().toString());
        data.put("studentNames", names);
        List<String> channels = List.of(absenceAlertChannels.split(","));
        notificationsApi.notifyUsers(guardianUserIds, "attendance.absent",
                "Absence recorded",
                String.join(", ", names) + " was marked absent on " + session.getSessionDate() + ".",
                data, channels);
    }

    // ----- reports -----

    public List<EventView> studentHistory(UUID studentId, LocalDate from, LocalDate to) {
        requireStudentAccess(studentId);
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        return eventRepository
                .findBySchoolIdAndStudentIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
                        schoolId(), studentId, fromInstant, toInstant)
                .stream().map(this::toEventView).toList();
    }

    public StudentSummary studentSummary(UUID studentId, LocalDate from, LocalDate to) {
        requireStudentAccess(studentId);
        Map<String, Long> counts = effectiveCounts(studentHistory(studentId, from, to));
        PeopleApi.StudentRef student = peopleApi.findStudent(schoolId(), studentId)
                .orElseThrow(() -> ApiException.notFound("student_not_found", "Student not found."));
        return new StudentSummary(studentId, student.fullName(), from, to,
                counts.getOrDefault("present", 0L), counts.getOrDefault("absent", 0L),
                counts.getOrDefault("late", 0L), counts.getOrDefault("excused", 0L));
    }

    public ClassReport classReport(UUID classGroupId, LocalDate from, LocalDate to) {
        UUID schoolId = schoolId();
        List<PeopleApi.StudentRef> roster = peopleApi.listStudentsByClass(schoolId, classGroupId, null);
        Instant fromInstant = from.atStartOfDay(zone).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<UUID, Map<String, Long>> byStudent = new HashMap<>();
        if (!roster.isEmpty()) {
            List<UUID> studentIds = roster.stream().map(PeopleApi.StudentRef::id).toList();
            List<EventView> events = eventRepository
                    .findBySchoolIdAndStudentIdInAndEventTimeGreaterThanEqualAndEventTimeLessThan(
                            schoolId, studentIds, fromInstant, toInstant)
                    .stream().map(this::toEventView).toList();
            Map<UUID, List<EventView>> grouped = new HashMap<>();
            for (EventView event : events) {
                grouped.computeIfAbsent(event.studentId(), k -> new ArrayList<>()).add(event);
            }
            for (Map.Entry<UUID, List<EventView>> entry : grouped.entrySet()) {
                byStudent.put(entry.getKey(), effectiveCounts(entry.getValue()));
            }
        }
        List<StudentReportRow> rows = new ArrayList<>();
        AttendanceCounts totals = new AttendanceCounts(0, 0, 0, 0, 0);
        long present = 0;
        long absent = 0;
        long late = 0;
        long excused = 0;
        for (PeopleApi.StudentRef student : roster) {
            Map<String, Long> counts = byStudent.getOrDefault(student.id(), Map.of());
            long p = counts.getOrDefault("present", 0L);
            long a = counts.getOrDefault("absent", 0L);
            long l = counts.getOrDefault("late", 0L);
            long e = counts.getOrDefault("excused", 0L);
            present += p;
            absent += a;
            late += l;
            excused += e;
            rows.add(new StudentReportRow(student.id(), student.fullName(), student.admissionNumber(),
                    p, a, l, e, rate(p, a, l, e)));
        }
        return new ClassReport(classGroupId, from, to, rows,
                new AttendanceCounts((int) present, (int) absent, (int) late, (int) excused, 0));
    }

    private Map<String, Long> effectiveCounts(List<EventView> events) {
        Map<UUID, EventView> latestPerSession = new HashMap<>();
        for (EventView event : events) {
            latestPerSession.merge(event.sessionId(), event,
                    (a, b) -> b.receivedAt() == null || (a.receivedAt() != null
                            && b.receivedAt().isAfter(a.receivedAt())) ? b : a);
        }
        Map<String, Long> counts = new HashMap<>();
        for (EventView event : latestPerSession.values()) {
            counts.merge(event.status(), 1L, Long::sum);
        }
        return counts;
    }

    private static double rate(long present, long absent, long late, long excused) {
        long total = present + absent + late + excused;
        if (total == 0) {
            return 0;
        }
        return Math.round((present + late) * 1000.0 / total) / 10.0;
    }

    private void requireStudentAccess(UUID studentId) {
        if (hasPermission(PermissionCatalog.ATTENDANCE_REPORT)) {
            return;
        }
        UUID actor = TenantContext.actorUserId();
        boolean self = peopleApi.studentForUser(schoolId(), actor)
                .map(s -> s.id().equals(studentId)).orElse(false);
        boolean guardian = peopleApi.parentCanView(schoolId(), actor, studentId, "attendance");
        if (!self && !guardian) {
            throw ApiException.forbidden("student_forbidden", "You cannot view this student's attendance.");
        }
    }

    private AttendanceSession requireSession(UUID sessionId) {
        return sessionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), sessionId)
                .orElseThrow(() -> ApiException.notFound("session_not_found", "Attendance session not found."));
    }

    private boolean hasPermission(String permission) {
        var principal = SecurityUtils.principalOrNull();
        return principal != null && principal.permissions() != null && principal.permissions().contains(permission);
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private SessionView toSessionView(AttendanceSession s) {
        return new SessionView(s.getId(), s.getClassGroupId(), s.getStreamId(), s.getSubjectId(),
                s.getTeacherMembershipId(), s.getSessionType(), s.getSessionDate(), s.getPeriodSeq(),
                s.getStatus(), s.getOpenedAt(), s.getClosedAt(), s.getNotes());
    }

    private EventView toEventView(AttendanceEvent e) {
        return new EventView(e.getId(), e.getSessionId(), e.getStudentId(), e.getStatus(), e.getReason(),
                e.getEventTime(), e.getReceivedAt(), e.getCorrectsEventId());
    }

    public record SessionView(UUID id, UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                              String sessionType, LocalDate sessionDate, Integer periodSeq, String status,
                              Instant openedAt, Instant closedAt, String notes) {
    }

    public record MarkInput(UUID studentId, String status, String reason) {
    }

    public record RosterRow(UUID studentId, String studentName, String admissionNumber, String status,
                            String reason, UUID eventId, Instant eventTime) {
    }

    public record AttendanceCounts(int present, int absent, int late, int excused, int unmarked) {
    }

    public record SessionDetailView(SessionView session, List<RosterRow> roster, AttendanceCounts counts) {
    }

    public record StudentSummary(UUID studentId, String studentName, LocalDate from, LocalDate to, long present,
                                 long absent, long late, long excused) {
    }

    public record StudentReportRow(UUID studentId, String studentName, String admissionNumber, long present,
                                   long absent, long late, long excused, double attendanceRate) {
    }

    public record ClassReport(UUID classGroupId, LocalDate from, LocalDate to, List<StudentReportRow> rows,
                              AttendanceCounts totals) {
    }
}
