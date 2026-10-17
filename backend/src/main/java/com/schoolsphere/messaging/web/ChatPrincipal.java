package com.schoolsphere.messaging.web;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Authenticated chat session identity resolved at STOMP CONNECT (spec 7.4). Implements
 * {@link Principal} so Spring can route /user/** destinations to the session (acks, errors).
 */
public record ChatPrincipal(UUID userId, UUID membershipId, UUID schoolId, Set<String> permissions,
                            String displayName) implements Principal {

    @Override
    public String getName() {
        return userId.toString();
    }
}
