package com.schoolsphere.subscriptions.internal;

import java.time.Duration;
import java.time.Instant;

/**
 * Subscription lifecycle rules (spec 13.2), expressed as pure state transitions over a
 * {@link SchoolSubscription} so they can be unit-tested without a database or a Spring context.
 *
 * <pre>
 * trial --expiry--&gt; past_due --grace expiry--&gt; suspended
 * trial/active/past_due --successful payment--&gt; active
 * active --period end (cancel scheduled)--&gt; cancelled
 * </pre>
 */
public final class SubscriptionLifecycle {

    public static final String TRIAL = "trial";
    public static final String ACTIVE = "active";
    public static final String PAST_DUE = "past_due";
    public static final String SUSPENDED = "suspended";
    public static final String CANCELLED = "cancelled";

    /** Billing period granted by a successful payment. */
    public static final Duration PERIOD = Duration.ofDays(30);

    /** Grace window after a trial/period expiry before access is suspended. */
    public static final Duration GRACE = Duration.ofDays(7);

    private SubscriptionLifecycle() {
    }

    /** Puts a subscription into its initial trial window. */
    static void startTrial(SchoolSubscription subscription, int trialDays, Instant now) {
        subscription.setStatus(TRIAL);
        subscription.setTrialStartedAt(now);
        subscription.setTrialEndsAt(now.plus(Duration.ofDays(Math.max(trialDays, 0))));
        subscription.setCurrentPeriodStart(null);
        subscription.setCurrentPeriodEnd(null);
        subscription.setGraceEndsAt(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
    }

    /**
     * Applies a settled payment: the subscription becomes active for a fresh billing period and any
     * scheduled cancellation is dropped (paying again is an explicit renewal).
     */
    static void applySuccessfulPayment(SchoolSubscription subscription, Instant now) {
        subscription.setStatus(ACTIVE);
        subscription.setCurrentPeriodStart(now);
        subscription.setCurrentPeriodEnd(now.plus(PERIOD));
        subscription.setGraceEndsAt(null);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setCancelledAt(null);
    }

    /**
     * Requests cancellation. During a trial it takes effect immediately (there is nothing to keep
     * paying for); otherwise it stops at the end of the paid period.
     */
    static void cancel(SchoolSubscription subscription, Instant now) {
        if (TRIAL.equals(subscription.getStatus())) {
            subscription.setStatus(CANCELLED);
            subscription.setCancelledAt(now);
            subscription.setCancelAtPeriodEnd(false);
            return;
        }
        subscription.setCancelAtPeriodEnd(true);
    }

    static void resume(SchoolSubscription subscription) {
        subscription.setCancelAtPeriodEnd(false);
    }

    /**
     * Reconciles time-based transitions. Returns the status after reconciliation; callers persist
     * only when the status actually changed.
     */
    static String advance(SchoolSubscription subscription, Instant now) {
        String status = subscription.getStatus();
        if (TRIAL.equals(status)) {
            Instant endsAt = subscription.getTrialEndsAt();
            if (endsAt != null && !now.isBefore(endsAt)) {
                return moveToPastDue(subscription, endsAt);
            }
            return status;
        }
        if (ACTIVE.equals(status)) {
            Instant endsAt = subscription.getCurrentPeriodEnd();
            if (endsAt != null && !now.isBefore(endsAt)) {
                if (subscription.isCancelAtPeriodEnd()) {
                    subscription.setStatus(CANCELLED);
                    subscription.setCancelledAt(endsAt);
                    subscription.setGraceEndsAt(null);
                    return CANCELLED;
                }
                return moveToPastDue(subscription, endsAt);
            }
            return status;
        }
        if (PAST_DUE.equals(status)) {
            Instant graceEndsAt = subscription.getGraceEndsAt();
            if (graceEndsAt != null && !now.isBefore(graceEndsAt)) {
                subscription.setStatus(SUSPENDED);
                return SUSPENDED;
            }
        }
        return status;
    }

    private static String moveToPastDue(SchoolSubscription subscription, Instant expiredAt) {
        subscription.setStatus(PAST_DUE);
        subscription.setGraceEndsAt(expiredAt.plus(GRACE));
        return PAST_DUE;
    }

    /** True when the school may keep using the platform (trial, paid, or inside the grace window). */
    public static boolean grantsAccess(String status) {
        return TRIAL.equals(status) || ACTIVE.equals(status) || PAST_DUE.equals(status);
    }
}
