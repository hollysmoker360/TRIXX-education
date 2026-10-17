package com.schoolsphere.subscriptions.internal;

import com.schoolsphere.subscriptions.api.PaymentProvider;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Mock payment provider for development and tests (ADR-007). It accepts any checkout and reports
 * every payment as immediately successful, so the full checkout → verify → activate flow can be
 * exercised without a real processor. Production replaces this bean with a Paystack implementation;
 * credentials live under {@code schoolsphere.billing.*} and are never shared with core school data.
 */
@Component
@Primary
public class MockPaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Checkout initialize(CheckoutRequest request) {
        final String reference = "mock-" + UUID.randomUUID();
        final String url = "http://localhost:8080/mock/pay?reference=" + reference
                + "&amount=" + request.amountMinor() + "&currency=" + request.currency();
        return new Checkout(reference, url);
    }

    @Override
    public Verification verify(String reference) {
        final Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("channel", "mock");
        metadata.put("verifiedAt", Instant.now().toString());
        metadata.put("simulated", true);
        return new Verification(reference, Verification.SUCCESS, 0, "KES", metadata);
    }
}
