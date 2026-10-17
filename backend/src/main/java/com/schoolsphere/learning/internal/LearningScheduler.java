package com.schoolsphere.learning.internal;

import com.schoolsphere.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Flips scheduled resources to published once their publish time has passed. Row-level security is
 * forced, so the job enumerates schools (the school table is not tenant-scoped) and runs the work
 * with an explicit tenant context per school.
 */
@Component
public class LearningScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final LearningService learningService;

    public LearningScheduler(JdbcTemplate jdbcTemplate, LearningService learningService) {
        this.jdbcTemplate = jdbcTemplate;
        this.learningService = learningService;
    }

    @Scheduled(fixedDelay = 60000)
    public void publishDueResources() {
        List<UUID> schoolIds = jdbcTemplate.queryForList(
                "SELECT id FROM school WHERE deleted_at IS NULL AND status = 'active'", UUID.class);
        for (UUID schoolId : schoolIds) {
            TenantContext.run(schoolId, null, null, () -> learningService.publishDueResources());
        }
    }
}
