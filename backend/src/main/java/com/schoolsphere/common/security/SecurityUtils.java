package com.schoolsphere.common.security;

import com.schoolsphere.common.error.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CurrentPrincipal principalOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CurrentPrincipal p) {
            return p;
        }
        return null;
    }

    public static CurrentPrincipal requirePrincipal() {
        CurrentPrincipal p = principalOrNull();
        if (p == null) {
            throw ApiException.unauthorized("unauthenticated", "Authentication is required.");
        }
        return p;
    }

    public static UUID requireUserId() {
        return requirePrincipal().userId();
    }

    public static UUID requireSchoolId() {
        CurrentPrincipal p = requirePrincipal();
        if (p.schoolId() == null) {
            throw ApiException.badRequest("tenant_required",
                    "Select a school first by sending the X-School-Id header.");
        }
        return p.schoolId();
    }

    public static UUID requireMembershipId() {
        CurrentPrincipal p = requirePrincipal();
        if (p.membershipId() == null) {
            throw ApiException.badRequest("tenant_required",
                    "Select a school first by sending the X-School-Id header.");
        }
        return p.membershipId();
    }
}
