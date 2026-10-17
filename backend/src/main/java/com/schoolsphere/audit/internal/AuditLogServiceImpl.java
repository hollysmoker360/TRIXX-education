package com.schoolsphere.audit.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.tenant.TenantContext;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogServiceImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String action, String entity, UUID entityId, Object beforeState, Object afterState) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO audit_log (school_id, actor_user_id, actor_membership_id, action, entity,
                                           entity_id, before_state, after_state, device_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """,
                    TenantContext.schoolId(),
                    TenantContext.actorUserId(),
                    TenantContext.actorMembershipId(),
                    action,
                    entity,
                    entityId,
                    toJson(beforeState),
                    toJson(afterState),
                    TenantContext.deviceId());
        } catch (Exception e) {
            log.warn("Audit write failed for action {}: {}", action, e.getMessage());
        }
    }

    @Override
    public void record(String action) {
        record(action, null, null, null, null);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
