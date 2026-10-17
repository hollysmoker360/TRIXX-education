package com.schoolsphere.subscriptions.web;

import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.subscriptions.api.PaymentProvider;
import com.schoolsphere.subscriptions.internal.SubscriptionService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for the subscriptions module (spec 13.2, ADR-007). All reads/writes run inside the
 * caller's tenant context, so a school can only ever touch its own billing state. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@TenantScoped
public class SubscriptionsController {

    private final SubscriptionService subscriptionService;
    private final PaymentProvider paymentProvider;

    public SubscriptionsController(SubscriptionService subscriptionService,
                                   @Qualifier("mockPaymentProvider") PaymentProvider paymentProvider) {
        this.subscriptionService = subscriptionService;
        this.paymentProvider = paymentProvider;
    }

    @GetMapping("/current")
    @PreAuthorize("@policy.can('school.view')")
    public SubscriptionService.SubscriptionView current() {
        return subscriptionService.current(SecurityUtils.requireSchoolId());
    }

    @GetMapping("/entitlements")
    @PreAuthorize("@policy.can('school.view')")
    public SubscriptionService.Entitlements entitlements() {
        return subscriptionService.entitlements(SecurityUtils.requireSchoolId());
    }

    @GetMapping("/catalogue")
    @PreAuthorize("@policy.can('subscription.manage')")
    public java.util.List<SubscriptionService.PlanView> catalogue() {
        return subscriptionService.catalogue();
    }

    @GetMapping("/history")
    @PreAuthorize("@policy.can('subscription.manage')")
    public java.util.List<SubscriptionService.PaymentView> history() {
        return subscriptionService.history(SecurityUtils.requireSchoolId());
    }

    @PostMapping("/checkout")
    @PreAuthorize("@policy.can('subscription.manage')")
    public SubscriptionService.CheckoutView checkout(@RequestBody CheckoutRequest request) {
        return subscriptionService.checkout(request.planCode(), request.callbackUrl());
    }

    @PostMapping("/verify")
    @PreAuthorize("@policy.can('subscription.manage')")
    public SubscriptionService.CheckoutResult verify(@RequestBody VerifyRequest request) {
        return subscriptionService.verify(request.reference());
    }

    @PostMapping("/cancel")
    @PreAuthorize("@policy.can('subscription.manage')")
    public SubscriptionService.SubscriptionView cancel() {
        return subscriptionService.cancel();
    }

    @PostMapping("/resume")
    @PreAuthorize("@policy.can('subscription.manage')")
    public SubscriptionService.SubscriptionView resume() {
        return subscriptionService.resume();
    }

    @GetMapping("/provider")
    @PreAuthorize("@policy.can('school.view')")
    public String provider() {
        return paymentProvider.name();
    }

    public record CheckoutRequest(String planCode, String callbackUrl) {
    }

    public record VerifyRequest(String reference) {
    }

    public record ProviderView(String name) {
    }
}
