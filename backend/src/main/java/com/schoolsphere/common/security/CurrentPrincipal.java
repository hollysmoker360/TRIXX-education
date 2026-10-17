package com.schoolsphere.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal for a request. Permissions are evaluated per membership and always
 * resolved from the database for the selected school (spec 6.2, 8.2).
 */
public record CurrentPrincipal(
        UUID userId,
        String displayName,
        String email,
        UUID schoolId,
        UUID membershipId,
        Set<String> permissions,
        String deviceId) {

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
