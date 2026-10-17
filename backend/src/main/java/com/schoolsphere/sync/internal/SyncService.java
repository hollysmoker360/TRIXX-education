package com.schoolsphere.sync.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.attendance.api.AttendanceApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.messaging.api.MessagingApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Offline subset sync (spec 10): push applies queued client operations idempotently by opId and
 * records the outcome; pull streams the tenant change feed with per-entity visibility filters.
 */
@Service
@TenantScoped
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final Set<String> PULLABLE_ENTITIES = Set.of(
            "task", "message", "channel", "channel_member",
            "timetable_version", "timetable_entry", "attendance_event", "attendance_session");
    private static final Set<String> TASK_FIELDS = Set.of(
            "title", "description", "dueAt", "priority", "category", "reminderAt", "done");
    private static final int DEFAULT_PULL_LIMIT = 200;
    private static final int MAX_PULL_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ChangeFeedReader changeFeedReader;
    private final MessagingApi messagingApi;
    private final AttendanceApi attendanceApi;

    public SyncService(JdbcTemplate jdbc, ObjectMapper objectMapper, ChangeFeedReader changeFeedReader,
                       MessagingApi messagingApi, AttendanceApi attendanceApi) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.changeFeedReader = changeFeedReader;
        this.messagingApi = messagingApi;
        this.attendanceApi = attendanceApi;
    }

    // ----- push -----

    public PushResult push(String deviceId, UUID batchId, List<OperationInput> operations) {
        UUID schoolId = schoolId();
        UUID userId = SecurityUtils.requireUserId();
        List<OperationResult> results = new ArrayList<>(operations.size());
        for (OperationInput op : operations) {
            OperationResult recorded = findRecorded(op.opId());
            if (recorded != null) {
                results.add(recorded);
                continue;
            }
            OperationResult result = applySafely(schoolId, userId, deviceId, op);
            record(op, deviceId, result);
            results.add(result);
        }
        return new PushResult(batchId, results);
    }

    private OperationResult applySafely(UUID schoolId, UUID userId, String deviceId, OperationInput op) {
        try {
            return switch (op.entity() == null ? "" : op.entity()) {
                case "task" -> applyTask(schoolId, userId, op);
                case "message" -> applyMessage(op);
                case "attendance_event" -> applyAttendanceEvent(deviceId, op);
                case "timetable_entry", "timetable_version" ->
                        rejected(op, "timetable_edits_online_only", "Timetable edits are online-only.");
                default -> rejected(op, "unsupported_entity", "This entity type is not synchronizable.");
            };
        } catch (ApiException e) {
            return rejected(op, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.warn("Sync push op {} ({}/{}) failed", op.opId(), op.entity(), op.type(), e);
            return rejected(op, "apply_failed", "The operation could not be applied.");
        }
    }

    private OperationResult applyTask(UUID schoolId, UUID userId, OperationInput op) {
        if (op.entityId() == null) {
            return rejected(op, "missing_entity_id", "A task id is required.");
        }
        return switch (op.type() == null ? "" : op.type()) {
            case "create" -> createTask(schoolId, userId, op);
            case "update" -> updateTask(schoolId, userId, op);
            case "delete" -> deleteTask(schoolId, userId, op);
            default -> rejected(op, "unsupported_op", "Unsupported operation type.");
        };
    }

    private OperationResult createTask(UUID schoolId, UUID userId, OperationInput op) {
        Map<String, Object> fields = fields(op);
        String title = str(fields, "title");
        if (title == null || title.isBlank()) {
            return rejected(op, "missing_title", "A task title is required.");
        }
        String priority = str(fields, "priority");
        if (priority != null && !Set.of("low", "medium", "high").contains(priority)) {
            return rejected(op, "invalid_priority", "Priority must be low, medium, or high.");
        }
        int inserted = jdbc.update("""
                INSERT INTO task (id, school_id, owner_user_id, owner_membership_id, title, description,
                                  due_at, priority, category, related_subject_id, related_assignment_id,
                                  task_key, reminder_at, version, created_at, updated_at, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, now(), now(), ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, op.entityId(), schoolId, userId, TenantContext.actorMembershipId(), title.trim(),
                str(fields, "description"), instant(fields, "dueAt"),
                priority == null ? "medium" : priority, str(fields, "category"),
                uuid(fields, "relatedSubjectId"), uuid(fields, "relatedAssignmentId"),
                str(fields, "taskKey"), instant(fields, "reminderAt"), userId, userId);
        if (inserted == 0) {
            JsonNode existing = taskSnapshot(schoolId, op.entityId());
            if (existing == null) {
                return rejected(op, "id_conflict", "The task id is already used by another record.");
            }
            if (!userId.equals(uuidOf(existing, "owner_user_id"))) {
                return rejected(op, "id_conflict", "The task id is already used by another record.");
            }
            return new OperationResult(op.opId(), op.entity(), op.entityId(), "duplicate", null, existing,
                    intOf(existing, "version"));
        }
        JsonNode snapshot = taskSnapshot(schoolId, op.entityId());
        return new OperationResult(op.opId(), op.entity(), op.entityId(), "applied", null, snapshot,
                intOf(snapshot, "version"));
    }

    private OperationResult updateTask(UUID schoolId, UUID userId, OperationInput op) {
        Map<String, Object> fields = fields(op);
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        for (String key : fields.keySet()) {
            if (!TASK_FIELDS.contains(key)) {
                continue;
            }
            switch (key) {
                case "title" -> {
                    String title = str(fields, "title");
                    if (title == null || title.isBlank()) {
                        return rejected(op, "missing_title", "A task title is required.");
                    }
                    sets.add("title = ?");
                    args.add(title.trim());
                }
                case "description" -> {
                    sets.add("description = ?");
                    args.add(str(fields, "description"));
                }
                case "dueAt" -> {
                    sets.add("due_at = ?");
                    args.add(instant(fields, "dueAt"));
                }
                case "priority" -> {
                    String priority = str(fields, "priority");
                    if (priority == null || !Set.of("low", "medium", "high").contains(priority)) {
                        return rejected(op, "invalid_priority", "Priority must be low, medium, or high.");
                    }
                    sets.add("priority = ?");
                    args.add(priority);
                }
                case "category" -> {
                    sets.add("category = ?");
                    args.add(str(fields, "category"));
                }
                case "reminderAt" -> {
                    sets.add("reminder_at = ?");
                    args.add(instant(fields, "reminderAt"));
                }
                case "done" -> {
                    sets.add("done_at = ?");
                    args.add(Boolean.TRUE.equals(fields.get("done")) ? java.sql.Timestamp.from(Instant.now()) : null);
                }
                default -> {
                    // unreachable: covered by TASK_FIELDS
                }
            }
        }
        if (sets.isEmpty()) {
            return rejected(op, "no_fields", "No updatable fields were provided.");
        }
        sets.add("updated_at = now()");
        sets.add("version = version + 1");
        sets.add("updated_by = ?");
        args.add(userId);
        args.add(schoolId);
        args.add(op.entityId());
        args.add(userId);
        int updated = jdbc.update("UPDATE task SET " + String.join(", ", sets)
                + " WHERE school_id = ? AND id = ? AND owner_user_id = ? AND deleted_at IS NULL", args.toArray());
        return taskWriteOutcome(schoolId, userId, op, updated);
    }

    private OperationResult deleteTask(UUID schoolId, UUID userId, OperationInput op) {
        int updated = jdbc.update("""
                UPDATE task SET deleted_at = now(), version = version + 1, updated_at = now(), updated_by = ?
                WHERE school_id = ? AND id = ? AND owner_user_id = ? AND deleted_at IS NULL
                """, userId, schoolId, op.entityId(), userId);
        return taskWriteOutcome(schoolId, userId, op, updated);
    }

    private OperationResult taskWriteOutcome(UUID schoolId, UUID userId, OperationInput op, int updated) {
        JsonNode snapshot = taskSnapshot(schoolId, op.entityId());
        if (updated == 1) {
            return new OperationResult(op.opId(), op.entity(), op.entityId(), "applied", null, snapshot,
                    intOf(snapshot, "version"));
        }
        if (snapshot != null && userId.equals(uuidOf(snapshot, "owner_user_id"))) {
            return new OperationResult(op.opId(), op.entity(), op.entityId(), "duplicate", null, snapshot,
                    intOf(snapshot, "version"));
        }
        return rejected(op, "task_not_found", "The task does not exist or is not yours.");
    }

    private JsonNode taskSnapshot(UUID schoolId, UUID entityId) {
        if (entityId == null) {
            return null;
        }
        List<JsonNode> rows = jdbc.query("SELECT to_jsonb(t) AS payload FROM task t WHERE school_id = ? AND id = ?",
                (rs, rowNum) -> readTree(rs.getString("payload")), schoolId, entityId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private OperationResult applyMessage(OperationInput op) {
        Map<String, Object> fields = fields(op);
        if (!"create".equals(op.type())) {
            return rejected(op, "offline_edit_unsupported",
                    "Messages can only be created offline; edits and deletes require a connection.");
        }
        UUID channelId = uuid(fields, "channelId");
        if (channelId == null) {
            return rejected(op, "missing_channel", "A channel id is required.");
        }
        List<Map<String, Object>> attachments = listOfMaps(fields.get("attachments"));
        UUID clientId = uuid(fields, "clientId");
        MessagingApi.SendOutcome outcome = messagingApi.sendMessage(new MessagingApi.MessageInput(
                op.entityId(), clientId == null ? op.entityId() : clientId, channelId,
                str(fields, "body"), uuid(fields, "replyToId"), attachments));
        JsonNode payload = objectMapper.valueToTree(outcome.message());
        return new OperationResult(op.opId(), op.entity(), op.entityId(),
                outcome.created() ? "applied" : "duplicate", null, payload, null);
    }

    private OperationResult applyAttendanceEvent(String deviceId, OperationInput op) {
        Map<String, Object> fields = fields(op);
        if (!"create".equals(op.type())) {
            return rejected(op, "unsupported_op", "Attendance events are append-only.");
        }
        UUID sessionId = uuid(fields, "sessionId");
        UUID studentId = uuid(fields, "studentId");
        String status = str(fields, "status");
        if (sessionId == null || studentId == null || status == null) {
            return rejected(op, "invalid_event", "sessionId, studentId, and status are required.");
        }
        AttendanceApi.EventOutcome outcome = attendanceApi.applyEvent(new AttendanceApi.EventInput(
                op.entityId(), sessionId, studentId, status, str(fields, "reason"),
                instant(fields, "eventTime"), deviceId, uuid(fields, "correctsEventId")));
        JsonNode payload = objectMapper.valueToTree(outcome.event());
        return new OperationResult(op.opId(), op.entity(), op.entityId(),
                outcome.created() ? "applied" : "duplicate", null, payload, null);
    }

    private OperationResult findRecorded(UUID opId) {
        if (opId == null) {
            return null;
        }
        List<OperationResult> rows = new ArrayList<>();
        jdbc.query("SELECT result::text AS result FROM sync_operation WHERE op_id = ? AND school_id = ?",
                rs -> {
                    try {
                        rows.add(objectMapper.readValue(rs.getString("result"), OperationResult.class));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new IllegalStateException(e);
                    }
                }, opId, schoolId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void record(OperationInput op, String deviceId, OperationResult result) {
        String json;
        try {
            json = objectMapper.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        jdbc.update("""
                INSERT INTO sync_operation (op_id, school_id, device_id, entity, entity_id, op_type, result,
                                            result_kind, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, now() + interval '90 days')
                ON CONFLICT (op_id) DO NOTHING
                """, op.opId(), schoolId(), deviceId, op.entity(), op.entityId(), op.type(), json,
                result.result());
    }

    private static OperationResult rejected(OperationInput op, String code, String message) {
        return new OperationResult(op.opId(), op.entity(), op.entityId(), "rejected", code + ": " + message,
                null, null);
    }

    // ----- pull -----

    public PullResult pull(Long cursor, Integer limit, String deviceId) {
        UUID schoolId = schoolId();
        UUID userId = SecurityUtils.requireUserId();
        UUID membershipId = TenantContext.actorMembershipId();
        long current = cursor == null ? 0L : Math.max(0L, cursor);
        int size = limit == null ? DEFAULT_PULL_LIMIT : Math.min(Math.max(limit, 1), MAX_PULL_LIMIT);

        List<ChangeFeedReader.ChangeRow> raw = changeFeedReader.read(schoolId, current, size + 1);
        boolean hasMore = raw.size() > size;
        if (hasMore) {
            raw = new ArrayList<>(raw.subList(0, size));
        }
        long newCursor = raw.isEmpty() ? current : raw.get(raw.size() - 1).seq();

        boolean resyncRequired = false;
        Long max = changeFeedReader.maxSeq(schoolId);
        Long min = changeFeedReader.minSeq(schoolId);
        if (max != null && current > max) {
            resyncRequired = true;
        }
        if (min != null && current > 0 && current < min - 1) {
            resyncRequired = true;
        }

        List<ChangeFeedReader.ChangeRow> visible = filterVisible(schoolId, userId, membershipId, raw);
        List<PullRow> rows = attachPayloads(schoolId, visible);

        if (deviceId != null && !deviceId.isBlank()) {
            jdbc.update("""
                    UPDATE sync_device SET last_pull_seq = ?, last_seen_at = now()
                    WHERE school_id = ? AND user_id = ? AND device_id = ?
                    """, newCursor, schoolId, userId, deviceId);
        }
        return new PullResult(newCursor, hasMore, resyncRequired, rows);
    }

    private List<ChangeFeedReader.ChangeRow> filterVisible(UUID schoolId, UUID userId, UUID membershipId,
                                                           List<ChangeFeedReader.ChangeRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> permissions = SecurityUtils.principalOrNull() == null
                ? Set.of() : SecurityUtils.principalOrNull().permissions();
        boolean staffAttendance = permissions.contains(PermissionCatalog.ATTENDANCE_TAKE)
                || permissions.contains(PermissionCatalog.ATTENDANCE_REPORT);
        Set<Long> allowed = new HashSet<>();

        Map<String, List<ChangeFeedReader.ChangeRow>> byEntity = rows.stream()
                .collect(Collectors.groupingBy(ChangeFeedReader.ChangeRow::entity,
                        LinkedHashMap::new, Collectors.toList()));

        List<ChangeFeedReader.ChangeRow> taskRows = byEntity.getOrDefault("task", List.of());
        if (!taskRows.isEmpty()) {
            Map<UUID, UUID> owners = uuidPairs(
                    "SELECT id, owner_user_id FROM task WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                    schoolId, ids(taskRows));
            taskRows.forEach(r -> {
                if (userId.equals(owners.get(r.entityId()))) {
                    allowed.add(r.seq());
                }
            });
        }

        List<ChangeFeedReader.ChangeRow> messageRows = byEntity.getOrDefault("message", List.of());
        if (!messageRows.isEmpty()) {
            Map<UUID, UUID> channels = uuidPairs(
                    "SELECT id, channel_id FROM message WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                    schoolId, ids(messageRows));
            Set<UUID> channelIds = new HashSet<>(channels.values());
            if (!channelIds.isEmpty()) {
                Set<UUID> mine = uuidSet(
                        "SELECT channel_id FROM channel_member WHERE school_id = ? AND membership_id = ? "
                                + "AND channel_id = ANY(string_to_array(?, ',')::uuid[])",
                        schoolId, membershipId, joinUuids(channelIds));
                messageRows.forEach(r -> {
                    if (mine.contains(channels.get(r.entityId()))) {
                        allowed.add(r.seq());
                    }
                });
            }
        }

        List<ChangeFeedReader.ChangeRow> eventRows = byEntity.getOrDefault("attendance_event", List.of());
        if (!eventRows.isEmpty()) {
            if (staffAttendance) {
                eventRows.forEach(r -> allowed.add(r.seq()));
            } else {
                Map<UUID, UUID> students = uuidPairs(
                        "SELECT id, student_id FROM attendance_event WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                        schoolId, ids(eventRows));
                Set<UUID> visibleStudents = uuidSet(
                        "SELECT id FROM student WHERE school_id = ? AND user_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                        schoolId, userId, joinUuids(new HashSet<>(students.values())));
                visibleStudents.addAll(uuidSet(
                        "SELECT gl.student_id FROM guardian_link gl "
                                + "JOIN parent_profile pp ON pp.school_id = gl.school_id AND pp.id = gl.parent_id "
                                + "WHERE gl.school_id = ? AND pp.user_id = ? AND pp.deleted_at IS NULL AND gl.deleted_at IS NULL "
                                + "AND coalesce(gl.visibilities ->> 'attendance', 'true') <> 'false' "
                                + "AND gl.student_id = ANY(string_to_array(?, ',')::uuid[])",
                        schoolId, userId, joinUuids(new HashSet<>(students.values()))));
                eventRows.forEach(r -> {
                    if (visibleStudents.contains(students.get(r.entityId()))) {
                        allowed.add(r.seq());
                    }
                });
            }
        }

        if (staffAttendance) {
            byEntity.getOrDefault("attendance_session", List.of()).forEach(r -> allowed.add(r.seq()));
        }

        List<ChangeFeedReader.ChangeRow> memberRows = byEntity.getOrDefault("channel_member", List.of());
        if (!memberRows.isEmpty()) {
            Map<UUID, UUID> memberships = uuidPairs(
                    "SELECT id, membership_id FROM channel_member WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                    schoolId, ids(memberRows));
            memberRows.forEach(r -> {
                if (membershipId != null && membershipId.equals(memberships.get(r.entityId()))) {
                    allowed.add(r.seq());
                }
            });
        }

        List<ChangeFeedReader.ChangeRow> channelRows = byEntity.getOrDefault("channel", List.of());
        if (!channelRows.isEmpty()) {
            Map<UUID, String> types = jdbc.query(
                    "SELECT id, channel_type FROM channel WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                    rs -> {
                        Map<UUID, String> map = new HashMap<>();
                        while (rs.next()) {
                            map.put(rs.getObject("id", UUID.class), rs.getString("channel_type"));
                        }
                        return map;
                    }, schoolId, ids(channelRows));
            channelRows.forEach(r -> {
                if (!"direct".equals(types.get(r.entityId()))) {
                    allowed.add(r.seq());
                }
            });
        }

        byEntity.getOrDefault("timetable_version", List.of()).forEach(r -> allowed.add(r.seq()));
        byEntity.getOrDefault("timetable_entry", List.of()).forEach(r -> allowed.add(r.seq()));

        return rows.stream().filter(r -> allowed.contains(r.seq())).toList();
    }

    private List<PullRow> attachPayloads(UUID schoolId, List<ChangeFeedReader.ChangeRow> rows) {
        Map<String, List<UUID>> upsertIdsByEntity = new LinkedHashMap<>();
        for (ChangeFeedReader.ChangeRow row : rows) {
            if ("upsert".equals(row.op())) {
                upsertIdsByEntity.computeIfAbsent(row.entity(), k -> new ArrayList<>()).add(row.entityId());
            }
        }
        Map<String, Map<UUID, JsonNode>> payloads = new HashMap<>();
        upsertIdsByEntity.forEach((entity, ids) ->
                payloads.put(entity, changeFeedReader.snapshotPayloads(schoolId, entity, ids)));

        List<PullRow> result = new ArrayList<>(rows.size());
        for (ChangeFeedReader.ChangeRow row : rows) {
            JsonNode payload = payloads.getOrDefault(row.entity(), Map.of()).get(row.entityId());
            result.add(new PullRow(row.seq(), row.entity(), row.entityId(), row.op(), row.version(),
                    row.changedAt(), row.actorUserId(), payload));
        }
        return result;
    }

    // ----- devices -----

    public DeviceView registerDevice(String deviceId, String name, String platform) {
        if (deviceId == null || deviceId.isBlank()) {
            throw ApiException.badRequest("missing_device_id", "A device id is required.");
        }
        UUID schoolId = schoolId();
        UUID userId = SecurityUtils.requireUserId();
        UUID id = jdbc.queryForObject("""
                INSERT INTO sync_device (school_id, user_id, device_id, name, platform)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (school_id, user_id, device_id)
                DO UPDATE SET name = EXCLUDED.name, platform = EXCLUDED.platform, last_seen_at = now(),
                              revoked_at = NULL
                RETURNING id
                """, (rs, i) -> rs.getObject("id", UUID.class), schoolId, userId, deviceId, name, platform);
        return deviceView(id);
    }

    public List<DeviceView> listDevices() {
        UUID schoolId = schoolId();
        UUID userId = SecurityUtils.requireUserId();
        return jdbc.query("""
                SELECT id, device_id, name, platform, last_pull_seq, last_seen_at, revoked_at
                FROM sync_device WHERE school_id = ? AND user_id = ? ORDER BY last_seen_at DESC
                """, (rs, i) -> new DeviceView(
                        rs.getObject("id", UUID.class),
                        rs.getString("device_id"),
                        rs.getString("name"),
                        rs.getString("platform"),
                        rs.getLong("last_pull_seq"),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                schoolId, userId);
    }

    public DeviceView revokeDevice(UUID id) {
        UUID schoolId = schoolId();
        int updated = jdbc.update(
                "UPDATE sync_device SET revoked_at = now() WHERE school_id = ? AND id = ? AND revoked_at IS NULL",
                schoolId, id);
        if (updated == 0) {
            throw ApiException.notFound("device_not_found", "Device not found.");
        }
        return deviceView(id);
    }

    private DeviceView deviceView(UUID id) {
        return jdbc.queryForObject("""
                SELECT id, device_id, name, platform, last_pull_seq, last_seen_at, revoked_at
                FROM sync_device WHERE school_id = ? AND id = ?
                """, (rs, i) -> new DeviceView(
                        rs.getObject("id", UUID.class),
                        rs.getString("device_id"),
                        rs.getString("name"),
                        rs.getString("platform"),
                        rs.getLong("last_pull_seq"),
                        rs.getTimestamp("last_seen_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                schoolId(), id);
    }

    // ----- helpers -----

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private static Map<String, Object> fields(OperationInput op) {
        return op.fields() == null ? Map.of() : op.fields();
    }

    private static String str(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Instant instant(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(text).toInstant();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private static String ids(List<ChangeFeedReader.ChangeRow> rows) {
        return joinUuids(rows.stream().map(ChangeFeedReader.ChangeRow::entityId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static String joinUuids(Set<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private Map<UUID, UUID> uuidPairs(String sql, Object... args) {
        Map<UUID, UUID> map = new HashMap<>();
        jdbc.query(sql, rs -> {
            if (rs.next()) {
                do {
                    map.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
                } while (rs.next());
            }
        }, args);
        return map;
    }

    private Set<UUID> uuidSet(String sql, Object... args) {
        Set<UUID> set = new HashSet<>();
        jdbc.query(sql, rs -> {
            while (rs.next()) {
                set.add(rs.getObject(1, UUID.class));
            }
        }, args);
        return set;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID uuidOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer intOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    // ----- DTOs -----

    public record OperationInput(UUID opId, String entity, UUID entityId, String type, Integer baseVersion,
                                 Map<String, Object> fields, Instant clientTime) {
    }

    public record OperationResult(UUID opId, String entity, UUID entityId, String result, String reason,
                                  JsonNode payload, Integer version) {
    }

    public record PushResult(UUID batchId, List<OperationResult> results) {
    }

    public record PullRow(long seq, String entity, UUID entityId, String op, Integer version, Instant changedAt,
                          UUID actorUserId, JsonNode payload) {
    }

    public record PullResult(long cursor, boolean hasMore, boolean resyncRequired, List<PullRow> changes) {
    }

    public record DeviceView(UUID id, String deviceId, String name, String platform, long lastPullSeq,
                             Instant lastSeenAt, Instant revokedAt) {
    }
}
