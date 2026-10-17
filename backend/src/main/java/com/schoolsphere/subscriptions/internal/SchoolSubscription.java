package com.schoolsphere.subscriptions.internal;

import com.schoolsphere.common.persistence.TenantEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One subscription per school with the lifecycle {@code trial -> active -> past_due -> suspended
 * -> cancelled} (spec 13.2). Transitions are decided by {@link SubscriptionLifecycle} so the rules
 * are unit-testable without a database.
 */
@Entity
@Table(name = "school_subscription", schema = "billing")
public class SchoolSubscription extends TenantEntity {

    UUID planId;
    String status = SubscriptionLifecycle.TRIAL;
    Instant trialStartedAt;
    Instant trialEndsAt;
    Instant currentPeriodStart;
    Instant currentPeriodEnd;
    Instant graceEndsAt;
    boolean cancelAtPeriodEnd;
    Instant cancelledAt;
    String providerCustomerCode;

    public UUID getPlanId() {
        return planId;
    }

    public void setPlanId(UUID planId) {
        this.planId = planId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTrialStartedAt() {
        return trialStartedAt;
    }

    public void setTrialStartedAt(Instant trialStartedAt) {
        this.trialStartedAt = trialStartedAt;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public void setTrialEndsAt(Instant trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public void setCurrentPeriodStart(Instant currentPeriodStart) {
        this.currentPeriodStart = currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
        this.currentPeriodEnd = currentPeriodEnd;
    }

    public Instant getGraceEndsAt() {
        return graceEndsAt;
    }

    public void setGraceEndsAt(Instant graceEndsAt) {
        this.graceEndsAt = graceEndsAt;
    }

    public boolean isCancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }

    public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
        this.cancelAtPeriodEnd = cancelAtPeriodEnd;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getProviderCustomerCode() {
        return providerCustomerCode;
    }

    public void setProviderCustomerCode(String providerCustomerCode) {
        this.providerCustomerCode = providerCustomerCode;
    }
}
