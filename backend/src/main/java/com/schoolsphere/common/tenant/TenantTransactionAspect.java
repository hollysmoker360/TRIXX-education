package com.schoolsphere.common.tenant;

import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opens a transaction for {@link TenantScoped} service calls and applies the transaction-local
 * tenant configuration (set_config(..., true)) before any statement executes, satisfying the
 * pattern in spec 7.2. Because the setting is transaction-local, connection-pool reuse cannot
 * leak tenant context. With no context set, row-level security returns zero rows.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantTransactionAspect {

    private static final String APPLIED_RESOURCE_KEY = "schoolsphere.tenantConfigApplied";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public TenantTransactionAspect(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    @Around("@annotation(com.schoolsphere.common.tenant.TenantScoped) "
            + "|| @within(com.schoolsphere.common.tenant.TenantScoped)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            throw new TenantRequiredException();
        }
        UUID actorUserId = TenantContext.actorUserId();

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            applyTenantConfig(schoolId, actorUserId);
            return joinPoint.proceed();
        }

        return transactionTemplate.execute(status -> {
            try {
                applyTenantConfig(schoolId, actorUserId);
                return joinPoint.proceed();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private void applyTenantConfig(UUID schoolId, UUID actorUserId) {
        if (TransactionSynchronizationManager.hasResource(APPLIED_RESOURCE_KEY)) {
            return;
        }
        jdbcTemplate.queryForObject("SELECT set_config('app.current_school', ?, true)", String.class,
                schoolId.toString());
        jdbcTemplate.queryForObject("SELECT set_config('app.actor_user', ?, true)", String.class,
                actorUserId != null ? actorUserId.toString() : "");
        TransactionSynchronizationManager.bindResource(APPLIED_RESOURCE_KEY, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(APPLIED_RESOURCE_KEY);
            }
        });
    }
}
