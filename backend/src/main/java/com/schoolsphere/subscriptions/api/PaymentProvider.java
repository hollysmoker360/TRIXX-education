package com.schoolsphere.subscriptions.api;

import java.util.Map;
import java.util.UUID;

/**
 * Payment provider seam (spec 7.5, ADR-007): dev and tests use the mock provider, production
 * targets Paystack. School-fee collection is out of scope; this interface only settles platform
 * subscription charges. Credentials live under {@code schoolsphere.billing.*} and are never shared
 * with the school data plane.
 */
public interface PaymentProvider {

    /** Stable provider identifier persisted on payment rows ({@code mock}, {@code paystack}). */
    String name();

    /** Creates a payment intent and returns the reference plus the URL the payer is sent to. */
    Checkout initialize(CheckoutRequest request);

    /** Re-reads a payment intent from the provider; never trust a client-reported status. */
    Verification verify(String reference);

    record CheckoutRequest(
            UUID schoolId,
            UUID subscriptionId,
            String planCode,
            long amountMinor,
            String currency,
            String payerEmail,
            String callbackUrl) {
    }

    record Checkout(String reference, String authorizationUrl) {
    }

    record Verification(
            String reference,
            String status,
            long amountMinor,
            String currency,
            Map<String, Object> metadata) {

        public static final String SUCCESS = "success";
        public static final String PENDING = "pending";
        public static final String FAILED = "failed";

        public boolean successful() {
            return SUCCESS.equalsIgnoreCase(status);
        }
    }
}
