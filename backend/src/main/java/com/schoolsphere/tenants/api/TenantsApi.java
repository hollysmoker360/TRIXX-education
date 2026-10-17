package com.schoolsphere.tenants.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public API of the tenants module. Other modules interact only through this package (spec 4.4).
 */
public interface TenantsApi {

    /** Creates a school with its owner membership and the default role bundles. */
    MembershipView createSchool(UUID ownerUserId, String name, String slug, String timezone);

    /** Validated resolution of a client-supplied school selection against the user's memberships. */
    Optional<MembershipView> resolveMembership(UUID userId, UUID schoolId);

    List<MembershipSummary> listMemberships(UUID userId);

    Optional<MembershipSummary> requireMembership(UUID membershipId);

    /** School settings JSON (spec 7.1); global table read without tenant context. */
    java.util.Map<String, Object> schoolSettings(UUID schoolId);

    record MembershipView(
            UUID membershipId,
            UUID schoolId,
            String schoolName,
            UUID userId,
            String status,
            String title,
            Set<String> permissions,
            List<String> roles) {
    }

    record MembershipSummary(
            UUID membershipId,
            UUID userId,
            UUID schoolId,
            String schoolName,
            String schoolSlug,
            String status,
            String title,
            List<String> roles) {
    }
}
