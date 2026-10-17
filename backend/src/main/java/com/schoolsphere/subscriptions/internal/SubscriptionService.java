package com.schoolsphere.subscriptions.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.subscriptions.api.PaymentProvider;
import com.schoolsphere.subscriptions.api.SubscriptionsApi;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Subscription lifecycle and checkout orchestration (spec 13.2, ADR-007). Every read/write runs in
 * the caller's tenant context: row-level security scopes the subscription and payment tables, so a
 * school can only ever see its own billing state. Payment settlement is always confirmed with the
 * provider ({@link PaymentProvider#verify}); a client-reported status is never trusted.
 */
@Service
@TenantScoped
public class SubscriptionService implements SubscriptionsApi {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final PaymentRepository payments;
    private final PaymentProvider paymentProvider;
    private final AuditLogService auditLogService;
    private final boolean billingEnabled;

    public SubscriptionService(SubscriptionRepository subscriptions,
                               PlanRepository plans,
                               PaymentRepository payments,
                               PaymentProvider paymentProvider,
                               AuditLogService auditLogService,
                               @Value("${schoolsphere.billing.enabled:false}") boolean billingEnabled) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.payments = payments;
        this.paymentProvider = paymentProvider;
        this.auditLogService = auditLogService;
        this.billingEnabled = billingEnabled;
    }

    public boolean isBillingEnabled() {
        return billingEnabled;
    }

    public String providerName() {
        return paymentProvider.name();
    }

    @Override
    public SubscriptionView current(UUID schoolId) {
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        return toView(subscription, planFor(subscription));
    }

    @Override
    public Entitlements entitlements(UUID schoolId) {
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        Plan plan = planFor(subscription);
        return new Entitlements(
                plan.getCode(),
                subscription.getStatus(),
                SubscriptionLifecycle.grantsAccess(subscription.getStatus()),
                SubscriptionLifecycle.TRIAL.equals(subscription.getStatus()),
                plan.getMaxStudents(),
                plan.getMaxTeachers(),
                List.copyOf(plan.getFeatures()));
    }

    /** Active plan catalogue with the school's current plan flagged, for the billing screen. */
    public List<PlanView> catalogue() {
        UUID schoolId = requireSchool();
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        return plans.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(plan -> new PlanView(
                        plan.getCode(),
                        plan.getName(),
                        plan.getDescription(),
                        plan.getPriceMinor(),
                        plan.getCurrency(),
                        plan.getIntervalUnit(),
                        plan.getMaxStudents(),
                        plan.getMaxTeachers(),
                        List.copyOf(plan.getFeatures()),
                        plan.getTrialDays(),
                        plan.getId().equals(subscription.getPlanId())))
                .toList();
    }

    /** Payment history for the school, newest first. */
    public List<PaymentView> history(UUID schoolId) {
        requireSchool();
        return payments.findBySchoolIdOrderByCreatedAtDesc(schoolId).stream()
                .map(payment -> new PaymentView(
                        payment.getId(),
                        payment.getProvider(),
                        payment.getProviderReference(),
                        payment.getAmountMinor(),
                        payment.getCurrency(),
                        payment.getStatus(),
                        payment.getChannel(),
                        payment.getFailureReason(),
                        payment.getPaidAt(),
                        payment.getCreatedAt()))
                .toList();
    }

    /** Creates a provider payment intent for the requested plan. Amounts come from the catalogue. */
    public CheckoutView checkout(String planCode, String callbackUrl) {
        UUID schoolId = requireSchool();
        if (!billingEnabled) {
            throw ApiException.forbidden("billing_disabled",
                    "Billing is not enabled for this deployment.");
        }
        Plan plan = plans.findByCodeAndActiveTrue(planCode)
                .orElseThrow(() -> ApiException.notFound("plan_not_found",
                        "No active plan with code '" + planCode + "'."));
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);

        PaymentProvider.Checkout checkout = paymentProvider.initialize(new PaymentProvider.CheckoutRequest(
                schoolId,
                subscription.getId(),
                plan.getCode(),
                plan.getPriceMinor(),
                plan.getCurrency(),
                SecurityUtils.principalOrNull() != null ? SecurityUtils.principalOrNull().email() : null,
                callbackUrl));

        Payment payment = new Payment();
        payment.setSchoolId(schoolId);
        payment.setSubscriptionId(subscription.getId());
        payment.setPlanId(plan.getId());
        payment.setProvider(paymentProvider.name());
        payment.setProviderReference(checkout.reference());
        payment.setAmountMinor(plan.getPriceMinor());
        payment.setCurrency(plan.getCurrency());
        payment.setStatus("pending");
        payments.save(payment);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("planCode", plan.getCode());
        after.put("reference", checkout.reference());
        after.put("amountMinor", plan.getPriceMinor());
        auditLogService.record("subscription.checkout.initiated", "payment", payment.getId(), null, after);

        return new CheckoutView(checkout.reference(), checkout.authorizationUrl(),
                plan.getPriceMinor(), plan.getCurrency(), paymentProvider.name());
    }

    /**
     * Confirms a payment with the provider and, on success, activates the subscription for a fresh
     * billing period. Idempotent: replaying a settled reference keeps the same subscription state.
     */
    public CheckoutResult verify(String reference) {
        UUID schoolId = requireSchool();
        Payment payment = payments.findByProviderAndProviderReference(paymentProvider.name(), reference)
                .orElseThrow(() -> ApiException.notFound("payment_not_found",
                        "No payment intent with reference '" + reference + "'."));

        PaymentProvider.Verification verification = paymentProvider.verify(reference);
        Instant now = Instant.now();

        if (!verification.successful()) {
            payment.setStatus(PaymentProvider.Verification.FAILED);
            payment.setFailureReason("provider status: " + verification.status());
            payment.setRawPayload(verification.metadata());
            payments.save(payment);
            auditLogService.record("subscription.payment.failed", "payment", payment.getId(),
                    null, Map.of("reference", reference, "providerStatus", verification.status()));
            return new CheckoutResult(false, payment.getStatus(), current(schoolId));
        }

        Plan plan = plans.findById(payment.getPlanId())
                .orElseThrow(() -> ApiException.notFound("plan_not_found", "The paying plan no longer exists."));

        payment.setStatus(PaymentProvider.Verification.SUCCESS);
        payment.setChannel(channelOf(verification));
        payment.setPaidAt(now);
        payment.setRawPayload(verification.metadata());
        payments.save(payment);

        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        Map<String, Object> before = lifecycleSnapshot(subscription);
        subscription.setPlanId(plan.getId());
        SubscriptionLifecycle.applySuccessfulPayment(subscription, now);
        SubscriptionLifecycle.advance(subscription, now);
        SchoolSubscription saved = subscriptions.save(subscription);

        auditLogService.record("subscription.activated", "school_subscription", saved.getId(),
                before, lifecycleSnapshot(saved));
        log.info("Subscription for school {} activated on plan {}", schoolId, plan.getCode());
        return new CheckoutResult(true, payment.getStatus(), toView(saved, plan));
    }

    /** Schedules cancellation (immediate during a trial). */
    public SubscriptionView cancel() {
        UUID schoolId = requireSchool();
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        Map<String, Object> before = lifecycleSnapshot(subscription);
        SubscriptionLifecycle.cancel(subscription, Instant.now());
        SchoolSubscription saved = subscriptions.save(subscription);
        auditLogService.record("subscription.cancel.requested", "school_subscription", saved.getId(),
                before, lifecycleSnapshot(saved));
        return toView(saved, planFor(saved));
    }

    /** Reverses a scheduled cancellation. Cancelled subscriptions must start a new checkout. */
    public SubscriptionView resume() {
        UUID schoolId = requireSchool();
        SchoolSubscription subscription = loadOrCreateTrial(schoolId);
        if (SubscriptionLifecycle.CANCELLED.equals(subscription.getStatus())) {
            throw ApiException.conflict("subscription_cancelled",
                    "A cancelled subscription cannot be resumed; start a new checkout instead.");
        }
        Map<String, Object> before = lifecycleSnapshot(subscription);
        SubscriptionLifecycle.resume(subscription);
        SchoolSubscription saved = subscriptions.save(subscription);
        auditLogService.record("subscription.cancel.reversed", "school_subscription", saved.getId(),
                before, lifecycleSnapshot(saved));
        return toView(saved, planFor(saved));
    }

    /**
     * Time-based reconciliation for the scheduler: advances every existing subscription that has
     * crossed a trial/period/grace boundary. Runs inside the tenant context handed in by the caller.
     */
    public int advanceExisting(UUID schoolId) {
        Optional<SchoolSubscription> existing = subscriptions.findBySchoolId(schoolId);
        if (existing.isEmpty()) {
            return 0;
        }
        SchoolSubscription subscription = existing.get();
        String before = subscription.getStatus();
        Instant now = Instant.now();
        Instant expiredAt = SubscriptionLifecycle.TRIAL.equals(before)
                ? subscription.getTrialEndsAt()
                : subscription.getCurrentPeriodEnd();
        String after = SubscriptionLifecycle.advance(subscription, now);
        if (after.equals(before)) {
            return 0;
        }
        SchoolSubscription saved = subscriptions.save(subscription);
        Map<String, Object> state = lifecycleSnapshot(saved);
        state.remove("status");
        Map<String, Object> beforeState = new LinkedHashMap<>(state);
        beforeState.put("status", before);
        auditLogService.record("subscription." + after, "school_subscription", saved.getId(),
                beforeState, lifecycleSnapshot(saved));
        log.info("School {} subscription moved {} -> {}", schoolId, before, after);
        return expiredAt == null ? 1 : 1;
    }

    private SchoolSubscription loadOrCreateTrial(UUID schoolId) {
        Optional<SchoolSubscription> existing = subscriptions.findBySchoolId(schoolId);
        if (existing.isPresent()) {
            SchoolSubscription subscription = existing.get();
            String before = subscription.getStatus();
            String after = SubscriptionLifecycle.advance(subscription, Instant.now());
            if (!after.equals(before)) {
                subscription = subscriptions.save(subscription);
            }
            return subscription;
        }
        Plan plan = defaultPlan();
        SchoolSubscription subscription = new SchoolSubscription();
        subscription.setSchoolId(schoolId);
        subscription.setPlanId(plan.getId());
        SubscriptionLifecycle.startTrial(subscription, plan.getTrialDays(), Instant.now());
        SchoolSubscription saved = subscriptions.save(subscription);
        auditLogService.record("subscription.trial.started", "school_subscription", saved.getId(), null,
                lifecycleSnapshot(saved));
        return saved;
    }

    private Plan defaultPlan() {
        return plans.findByActiveTrueOrderBySortOrderAsc().stream()
                .min(Comparator.comparingInt(Plan::getSortOrder))
                .orElseThrow(() -> ApiException.preconditionFailed("no_plans_configured",
                        "No active billing plan is configured on this deployment."));
    }

    private Plan planFor(SchoolSubscription subscription) {
        return plans.findById(subscription.getPlanId())
                .orElseGet(this::defaultPlan);
    }

    private UUID requireSchool() {
        UUID schoolId = TenantContext.schoolId();
        if (schoolId == null) {
            schoolId = SecurityUtils.requireSchoolId();
        }
        return schoolId;
    }

    private static String channelOf(PaymentProvider.Verification verification) {
        Object channel = verification.metadata() == null ? null : verification.metadata().get("channel");
        return channel == null ? null : channel.toString();
    }

    private static Map<String, Object> lifecycleSnapshot(SchoolSubscription subscription) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", subscription.getStatus());
        snapshot.put("planId", subscription.getPlanId());
        snapshot.put("trialEndsAt", subscription.getTrialEndsAt());
        snapshot.put("currentPeriodEnd", subscription.getCurrentPeriodEnd());
        snapshot.put("graceEndsAt", subscription.getGraceEndsAt());
        snapshot.put("cancelAtPeriodEnd", subscription.isCancelAtPeriodEnd());
        return snapshot;
    }

    private static SubscriptionView toView(SchoolSubscription subscription, Plan plan) {
        return new SubscriptionView(
                subscription.getId(),
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                subscription.getStatus(),
                subscription.getTrialEndsAt(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getGraceEndsAt(),
                subscription.isCancelAtPeriodEnd(),
                SubscriptionLifecycle.grantsAccess(subscription.getStatus()));
    }

    public record PlanView(
            String code,
            String name,
            String description,
            long priceMinor,
            String currency,
            String intervalUnit,
            Integer maxStudents,
            Integer maxTeachers,
            List<String> features,
            int trialDays,
            boolean current) {
    }

    public record PaymentView(
            UUID id,
            String provider,
            String reference,
            long amountMinor,
            String currency,
            String status,
            String channel,
            String failureReason,
            Instant paidAt,
            Instant createdAt) {
    }

    public record CheckoutView(
            String reference,
            String authorizationUrl,
            long amountMinor,
            String currency,
            String provider) {
    }

    public record CheckoutResult(boolean success, String paymentStatus, SubscriptionView subscription) {
    }
}
