package com.schoolsphere.auth.api;

import java.util.Optional;
import java.util.UUID;

/** Verifies access tokens outside the servlet filter chain (WebSocket handshake, spec 7.4). */
public interface TokenVerifier {

    Optional<VerifiedToken> verifyAccessToken(String rawToken);

    record VerifiedToken(UUID userId, String displayName, String email) {
    }
}
