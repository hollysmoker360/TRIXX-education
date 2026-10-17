package com.schoolsphere.auth.security;

import com.schoolsphere.common.security.CurrentPrincipal;
import com.schoolsphere.common.security.SecurityUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Central authorization policy (spec 8.2). Controllers never contain permission logic; method
 * checks use @PreAuthorize("@policy.can(...)"). Record-level rules live in policy classes.
 */
@Component("policy")
public class AuthorizationPolicy {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationPolicy.class);

    /**
     * Checks a permission for the current principal against the selected school. A school id that
     * does not match the current selection is denied.
     */
    public boolean can(UUID schoolId, String permission) {
        CurrentPrincipal principal = SecurityUtils.principalOrNull();
        if (principal == null) {
            return false;
        }
        if (schoolId != null && principal.schoolId() != null && !schoolId.equals(principal.schoolId())) {
            log.warn("Permission check for school {} rejected: selected school is {}", schoolId, principal.schoolId());
            return false;
        }
        return principal.hasPermission(permission);
    }

    public boolean can(String permission) {
        return can(null, permission);
    }

    /** True when the principal is a member of the school with an active membership. */
    public boolean memberOf(UUID schoolId) {
        CurrentPrincipal principal = SecurityUtils.principalOrNull();
        return principal != null && principal.schoolId() != null && principal.schoolId().equals(schoolId);
    }
}
