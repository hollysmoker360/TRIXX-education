package com.schoolsphere.notifications.internal;

import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Tenant-scoped state transitions for queued notifications (called by {@link OutboxWorker}). */
@Service
@TenantScoped
public class NotificationStateService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStateService.class);

    private final NotificationRepository notificationRepository;

    public NotificationStateService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void markSent(UUID notificationId) {
        notificationRepository.findBySchoolIdAndId(TenantContext.schoolId(), notificationId).ifPresent(n -> {
            if (!"read".equals(n.getState())) {
                n.setState("sent");
            }
            n.setSentAt(Instant.now());
            notificationRepository.save(n);
        });
    }

    public void markFailed(UUID notificationId, String reason) {
        log.warn("notification.failed id={} reason={}", notificationId, reason);
        notificationRepository.findBySchoolIdAndId(TenantContext.schoolId(), notificationId).ifPresent(n -> {
            if (!"read".equals(n.getState())) {
                n.setState("failed");
            }
            notificationRepository.save(n);
        });
    }
}
