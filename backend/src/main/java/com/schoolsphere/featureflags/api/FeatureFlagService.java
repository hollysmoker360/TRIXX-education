package com.schoolsphere.featureflags.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Table-backed feature flags (spec 3.2). A flag is either global ({@code school_id IS NULL}) or
 * scoped to a single school; a school-specific flag overrides the global default. Evaluation runs
 * in the caller's tenant context, so row-level security already scopes results to the current
 * school plus globals. Global flags are seeded via SQL; this service manages school overrides.
 */
public interface FeatureFlagService {

    /** True when the flag is enabled for the current school (school override wins over global). */
    boolean enabled(String flagKey);

    /** The resolved flag view for the current school, if any global or school flag exists. */
    Optional<FlagView> flag(String flagKey);

    /** All flags visible in the current tenant context (school overrides + globals). */
    List<FlagView> flags();

    /**
     * Creates or updates a school-scoped flag override. Requires {@code school.settings.manage}.
     * Global flags (school_id NULL) are managed via SQL/seeding, not through this endpoint.
     */
    FlagView set(String flagKey, boolean enabled, Map<String, Object> payload);

    record FlagView(String flagKey, UUID schoolId, boolean enabled, Map<String, Object> payload) {}
}
