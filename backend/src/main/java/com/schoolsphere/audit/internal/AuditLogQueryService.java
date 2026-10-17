package com.schoolsphere.audit.internal;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Read side of the append-only audit log (spec 13.2). Rows are written with JdbcTemplate by
 * {@link AuditLogServiceImpl}; reads follow the same tenant-scoped JDBC path so row-level security
 * and the explicit school_id filter agree (spec 7.2). No update or delete: the log is append-only.
 */
@Service
@TenantScoped
public class AuditLogQueryService {

    public static final int MAX_SIZE = 200;

    private static final int EXPORT_MAX_ROWS = 10_000;

    private static final String COLUMNS = "id, school_id, actor_user_id, actor_membership_id, action, entity, "
            + "entity_id, before_state, after_state, device_id, ip_address, created_at";

    private static final String STABLE_ORDER = " ORDER BY created_at DESC, id DESC";

    private static final RowMapper<AuditLogEntry> ENTRY_ROW_MAPPER = AuditLogQueryService::mapEntry;

    private final JdbcTemplate jdbcTemplate;

    public AuditLogQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<AuditLogEntry> query(String action, String entity, UUID entityId, Instant from, Instant to,
                                             int page, int size) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.min(Math.max(size, 1), MAX_SIZE);
        List<Object> params = new ArrayList<>();
        String where = whereClause(params, action, entity, entityId, from, to);
        Long total = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log" + where, Long.class,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(resolvedSize);
        pageParams.add(resolvedPage * resolvedSize);
        List<AuditLogEntry> items = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log" + where + STABLE_ORDER + " LIMIT ? OFFSET ?",
                ENTRY_ROW_MAPPER, pageParams.toArray());
        return PageResponse.of(items, resolvedPage, resolvedSize, total == null ? 0 : total);
    }

    public AuditLogEntry get(UUID id) {
        List<Object> params = new ArrayList<>();
        params.add(TenantContext.schoolId());
        params.add(id);
        List<AuditLogEntry> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log WHERE school_id = ? AND id = ?",
                ENTRY_ROW_MAPPER, params.toArray());
        return rows.stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("audit_entry_not_found", "Audit entry not found."));
    }

    public byte[] exportCsv(String action, String entity, UUID entityId, Instant from, Instant to) {
        List<Object> params = new ArrayList<>();
        String where = whereClause(params, action, entity, entityId, from, to);
        params.add(EXPORT_MAX_ROWS);
        List<AuditLogEntry> entries = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM audit_log" + where + STABLE_ORDER + " LIMIT ?",
                ENTRY_ROW_MAPPER, params.toArray());
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Id,Created at,Action,Entity,Entity id,Actor user id,Actor membership id,Device id,"
                + " IP address,Before state,After state\n");
        for (AuditLogEntry entry : entries) {
            sb.append(csv(entry.id() == null ? null : entry.id().toString())).append(',')
                    .append(csv(entry.createdAt() == null ? null : entry.createdAt().toString())).append(',')
                    .append(csv(entry.action())).append(',')
                    .append(csv(entry.entity())).append(',')
                    .append(csv(entry.entityId() == null ? null : entry.entityId().toString())).append(',')
                    .append(csv(entry.actorUserId() == null ? null : entry.actorUserId().toString())).append(',')
                    .append(csv(entry.actorMembershipId() == null ? null : entry.actorMembershipId().toString()))
                    .append(',')
                    .append(csv(entry.deviceId())).append(',')
                    .append(csv(entry.ipAddress())).append(',')
                    .append(csv(entry.beforeState())).append(',')
                    .append(csv(entry.afterState())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String whereClause(List<Object> params, String action, String entity, UUID entityId, Instant from,
                               Instant to) {
        StringBuilder where = new StringBuilder(" WHERE school_id = ?");
        params.add(TenantContext.schoolId());
        if (action != null && !action.isBlank()) {
            where.append(" AND action = ?");
            params.add(action.trim());
        }
        if (entity != null && !entity.isBlank()) {
            where.append(" AND entity = ?");
            params.add(entity.trim());
        }
        if (entityId != null) {
            where.append(" AND entity_id = ?");
            params.add(entityId);
        }
        if (from != null) {
            where.append(" AND created_at >= ?");
            params.add(from);
        }
        if (to != null) {
            where.append(" AND created_at <= ?");
            params.add(to);
        }
        return where.toString();
    }

    private static AuditLogEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLogEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("school_id", UUID.class),
                rs.getObject("actor_user_id", UUID.class),
                rs.getObject("actor_membership_id", UUID.class),
                rs.getString("action"),
                rs.getString("entity"),
                rs.getObject("entity_id", UUID.class),
                rs.getString("before_state"),
                rs.getString("after_state"),
                rs.getString("device_id"),
                rs.getString("ip_address"),
                rs.getObject("created_at", Instant.class));
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    public record AuditLogEntry(UUID id, UUID schoolId, UUID actorUserId, UUID actorMembershipId, String action,
                                String entity, UUID entityId, String beforeState, String afterState, String deviceId,
                                String ipAddress, Instant createdAt) {
    }
}
