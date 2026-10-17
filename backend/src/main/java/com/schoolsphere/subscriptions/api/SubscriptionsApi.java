package com.schoolsphere.subscriptions.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Public API of the subscriptions module (spec 4.4). Callers must already run inside the school's
 * tenant context (row-level security scopes every read); this mirrors the other module APIs that
 * take an explicit {@code schoolId}.
 */
public interface SubscriptionsApi {

    /** Current subscription for the school, starting a trial on first access if none exists. */
    SubscriptionView current(UUID schoolId);

    /** Plan entitlements used for feature gating and seat/roster limits. */
    Entitlements entitlements(UUID schoolId);

    record SubscriptionView(
            UUID id,
            UUID planId,
            String planCode,
            String planName,
            String status,
            Instant trialEndsAt,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant graceEndsAt,
            boolean cancelAtPeriodEnd,
            boolean billable) {
    }

    record Entitlements(
            String planCode,
            String status,
            boolean active,
            boolean inTrial,
            Integer maxStudents,
            Integer maxTeachers,
            List<String> features) {
    }
}
