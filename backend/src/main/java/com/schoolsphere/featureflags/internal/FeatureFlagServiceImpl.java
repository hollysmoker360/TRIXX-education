package com.schoolsphere.featureflags.internal;

import com.schoolsphere.featureflags.api.FeatureFlagService;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Evaluation and management of table-backed feature flags (spec 3.2). Resolution order for the
 * current school: school-specific override first, then the global default. Absent any row, the
 * flag is disabled.
 */
@Service
@TenantScoped
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagServiceImpl.class);

    private final FeatureFlagRepository repository;

    public FeatureFlagServiceImpl(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean enabled(String flagKey) {
        return flag(flagKey).map(FeatureFlagService.FlagView::enabled).orElse(false);
    }

    @Override
    public Optional<FlagView> flag(String flagKey) {
        UUID schoolId = TenantContext.schoolId();
        List<FeatureFlagEntity> rows = repository.findByFlagKey(flagKey);
        FeatureFlagEntity schoolRow = rows.stream()
                .filter(e -> schoolId != null && schoolId.equals(e.getSchoolId()))
                .findFirst().orElse(null);
        if (schoolRow != null) {
            return Optional.of(toView(schoolRow));
        }
        return rows.stream()
                .filter(e -> e.getSchoolId() == null)
                .findFirst()
                .map(this::toView);
    }

    @Override
    public List<FlagView> flags() {
        return repository.findAll().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public FlagView set(String flagKey, boolean enabled, Map<String, Object> payload) {
        requireManagePermission();
        UUID schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            throw ApiException.badRequest("tenant_required",
                    "Select a school first to set a flag override.");
        }
        FeatureFlagEntity entity = repository.findByFlagKeyAndSchoolId(flagKey, schoolId)
                .orElseGet(() -> {
                    FeatureFlagEntity created = new FeatureFlagEntity();
                    created.setFlagKey(flagKey);
                    created.setSchoolId(schoolId);
                    return created;
                });
        entity.setEnabled(enabled);
        entity.setPayload(payload == null ? Map.of() : new LinkedHashMap<>(payload));
        FeatureFlagEntity saved = repository.save(entity);
        log.info("Feature flag {} set for school {}: enabled={}", flagKey, schoolId, enabled);
        return toView(saved);
    }

    private void requireManagePermission() {
        if (!SecurityUtils.principalOrNull().hasPermission("school.settings.manage")) {
            throw ApiException.forbidden("feature_flag_manage_required",
                    "The school.settings.manage permission is required to set feature flags.");
        }
    }

    private FlagView toView(FeatureFlagEntity entity) {
        return new FlagView(entity.getFlagKey(), entity.getSchoolId(), entity.isEnabled(),
                Map.copyOf(entity.getPayload()));
    }
}
