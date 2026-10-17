package com.schoolsphere.audit.api;

import java.util.UUID;

/**
 * Append-only audit trail for logins, role changes, grade changes, data exports, impersonation,
 * subscription changes, and sync device events (spec 13.2).
 */
public interface AuditLogService {

    void record(String action, String entity, UUID entityId, Object beforeState, Object afterState);

    void record(String action);

    static AuditLogService noop() {
        return new AuditLogService() {
            @Override
            public void record(String action, String entity, UUID entityId, Object beforeState, Object afterState) {
            }

            @Override
            public void record(String action) {
            }
        };
    }
}
