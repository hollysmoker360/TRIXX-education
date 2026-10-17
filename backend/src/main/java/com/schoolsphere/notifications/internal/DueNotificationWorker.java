package com.schoolsphere.notifications.internal;

import com.schoolsphere.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases scheduled reminders when they are due (spec 12.2). Row-level security is forced, so the
 * job enumerates schools (the school table is not tenant-scoped) and runs the release with an
 * explicit tenant context per school, exactly like {@code LearningScheduler}.
 */
@Component
public class DueNotificationWorker {

    private final JdbcTemplate jdbcTemplate;
    private final ScheduledNotificationReleaser releaser;

    public DueNotificationWorker(JdbcTemplate jdbcTemplate, ScheduledNotificationReleaser releaser) {
        this.jdbcTemplate = jdbcTemplate;
        this.releaser = releaser;
    }

    @Scheduled(fixedDelayString = "${schoolsphere.notifications.due-delay-ms:15000}")
    public void poll() {
        List<UUID> schoolIds = jdbcTemplate.queryForList(
                "SELECT id FROM school WHERE deleted_at IS NULL AND status = 'active'", UUID.class);
        for (UUID schoolId : schoolIds) {
            TenantContext.run(schoolId, null, null, releaser::releaseDue);
        }
    }
}
