package com.schoolsphere.featureflags.web;

import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.featureflags.api.FeatureFlagService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for table-backed feature flags (spec 3.2). Evaluation runs inside the caller's
 * tenant context, so row-level security already scopes the returned flags. */
@RestController
@RequestMapping("/api/v1/feature-flags")
@TenantScoped
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    public FeatureFlagController(FeatureFlagService featureFlagService) {
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    @PreAuthorize("@policy.can('school.view')")
    public List<FeatureFlagService.FlagView> flags() {
        return featureFlagService.flags();
    }

    @GetMapping("/{flagKey}")
    @PreAuthorize("@policy.can('school.view')")
    public Optional<FeatureFlagService.FlagView> flag(@PathVariable String flagKey) {
        return featureFlagService.flag(flagKey);
    }

    @PutMapping("/{flagKey}")
    @PreAuthorize("@policy.can('school.settings.manage')")
    public FeatureFlagService.FlagView setFlag(
            @PathVariable String flagKey,
            @RequestBody FlagOverride request) {
        return featureFlagService.set(flagKey, request.enabled(), request.payload());
    }

    record FlagOverride(Boolean enabled, Map<String, Object> payload) {
    }
}
