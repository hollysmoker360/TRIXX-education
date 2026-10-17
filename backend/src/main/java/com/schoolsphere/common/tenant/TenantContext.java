package com.schoolsphere.common.tenant;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Per-request tenant context. The tenant is derived from the authenticated principal and its
 * memberships (spec 7.1); a client-supplied school id is only a selection among the user's
 * memberships and is always validated before the context is set.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> SCHOOL = new ThreadLocal<>();
    private static final ThreadLocal<UUID> ACTOR_USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> ACTOR_MEMBERSHIP = new ThreadLocal<>();
    private static final ThreadLocal<String> DEVICE = new ThreadLocal<>();

    private TenantContext() {
    }

    public static UUID schoolId() {
        return SCHOOL.get();
    }

    public static UUID actorUserId() {
        return ACTOR_USER.get();
    }

    public static UUID actorMembershipId() {
        return ACTOR_MEMBERSHIP.get();
    }

    public static String deviceId() {
        return DEVICE.get();
    }

    public static void set(UUID schoolId, UUID actorUserId, UUID actorMembershipId, String deviceId) {
        SCHOOL.set(schoolId);
        ACTOR_USER.set(actorUserId);
        ACTOR_MEMBERSHIP.set(actorMembershipId);
        DEVICE.set(deviceId);
    }

    public static void clear() {
        SCHOOL.remove();
        ACTOR_USER.remove();
        ACTOR_MEMBERSHIP.remove();
        DEVICE.remove();
    }

    /** Runs a block with an explicit tenant context (seeding, background jobs, tests). */
    public static <T> T run(UUID schoolId, UUID actorUserId, UUID actorMembershipId, Callable<T> block) {
        UUID prevSchool = SCHOOL.get();
        UUID prevUser = ACTOR_USER.get();
        UUID prevMembership = ACTOR_MEMBERSHIP.get();
        String prevDevice = DEVICE.get();
        try {
            set(schoolId, actorUserId, actorMembershipId, null);
            return block.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            if (prevSchool != null) {
                set(prevSchool, prevUser, prevMembership, prevDevice);
            } else {
                clear();
            }
        }
    }

    public static void run(Runnable block) {
        UUID prevSchool = SCHOOL.get();
        UUID prevUser = ACTOR_USER.get();
        UUID prevMembership = ACTOR_MEMBERSHIP.get();
        String prevDevice = DEVICE.get();
        try {
            block.run();
        } finally {
            if (prevSchool != null) {
                set(prevSchool, prevUser, prevMembership, prevDevice);
            } else {
                clear();
            }
        }
    }
}
