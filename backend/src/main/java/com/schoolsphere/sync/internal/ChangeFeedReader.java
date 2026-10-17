package com.schoolsphere.sync.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the per-tenant change feed (spec 10.2). Row-level security scopes every query to the
 * current school, so the reader never needs an explicit school filter to stay safe.
 */
@Component
public class ChangeFeedReader {

    private static final Map<String, String> SNAPSHOT_TABLES = Map.ofEntries(
            Map.entry("task", "task"),
            Map.entry("message", "message"),
            Map.entry("channel", "channel"),
            Map.entry("channel_member", "channel_member"),
            Map.entry("attendance_event", "attendance_event"),
            Map.entry("attendance_session", "attendance_session"),
            Map.entry("timetable_version", "timetable_version"),
            Map.entry("timetable_entry", "timetable_entry"));

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChangeFeedReader(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public record ChangeRow(long seq, String entity, UUID entityId, String op, Integer version,
                            Instant changedAt, UUID actorUserId, JsonNode payload) {
    }

    public List<ChangeRow> read(UUID schoolId, long afterSeq, int limit) {
        return jdbc.query("""
                SELECT change_seq, entity, entity_id, op, version, changed_at, actor_user_id
                FROM change_log
                WHERE school_id = ? AND change_seq > ?
                ORDER BY change_seq ASC
                LIMIT ?
                """, (rs, i) -> new ChangeRow(
                        rs.getLong("change_seq"),
                        rs.getString("entity"),
                        rs.getObject("entity_id", UUID.class),
                        rs.getString("op"),
                        (Integer) rs.getObject("version"),
                        rs.getTimestamp("changed_at").toInstant(),
                        rs.getObject("actor_user_id", UUID.class),
                        null), schoolId, afterSeq, limit);
    }

    public Long minSeq(UUID schoolId) {
        return jdbc.queryForObject("SELECT min(change_seq) FROM change_log WHERE school_id = ?", Long.class, schoolId);
    }

    public Long maxSeq(UUID schoolId) {
        return jdbc.queryForObject("SELECT max(change_seq) FROM change_log WHERE school_id = ?", Long.class, schoolId);
    }

    /** Snapshot payloads (to_jsonb of the source row) for upsert rows of one entity type. */
    public Map<UUID, JsonNode> snapshotPayloads(UUID schoolId, String entity, Collection<UUID> ids) {
        String table = SNAPSHOT_TABLES.get(entity);
        if (table == null || ids.isEmpty()) {
            return Map.of();
        }
        String idList = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        Map<UUID, JsonNode> result = new HashMap<>();
        jdbc.query(String.format(
                        "SELECT id, to_jsonb(t) AS payload FROM %s t WHERE school_id = ? AND id = ANY(string_to_array(?, ',')::uuid[])",
                        table),
                rs -> {
                    result.put(rs.getObject("id", UUID.class), readTree(rs.getString("payload")));
                },
                schoolId, idList);
        return result;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
