package com.schoolsphere.timetable.internal;

import com.schoolsphere.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps a published timetable rolled forward (spec 12.2): occurrences are materialised a horizon of
 * days ahead, so a reminder is always scheduled well before the lesson it belongs to. Row-level
 * security is forced, so the job enumerates schools and runs the work with an explicit tenant
 * context, like the other scheduled jobs.
 */
@Component
public class LessonScheduleWorker {

    private final JdbcTemplate jdbcTemplate;
    private final LessonScheduleService lessonScheduleService;

    public LessonScheduleWorker(JdbcTemplate jdbcTemplate, LessonScheduleService lessonScheduleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.lessonScheduleService = lessonScheduleService;
    }

    @Scheduled(fixedDelayString = "${schoolsphere.timetable.schedule-delay-ms:3600000}")
    public void rollForward() {
        List<UUID> schoolIds = jdbcTemplate.queryForList(
                "SELECT id FROM school WHERE deleted_at IS NULL AND status = 'active'", UUID.class);
        for (UUID schoolId : schoolIds) {
            TenantContext.run(schoolId, null, null, lessonScheduleService::rollForwardAllPublished);
        }
    }
}
