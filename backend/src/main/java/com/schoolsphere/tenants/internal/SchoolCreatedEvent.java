package com.schoolsphere.tenants.internal;

import java.util.UUID;

/** Published when a school is created so other modules can seed their defaults. */
public record SchoolCreatedEvent(UUID schoolId, UUID ownerUserId, UUID ownerMembershipId, String schoolName) {
}
