package com.schoolsphere.auth.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT access tokens (10-15 minutes) signed with rotatable keys identified by the kid header
 * (spec 8.1). The first configured key signs; every configured key verifies so rotation is
 * non-disruptive.
 */
@Component
public class JwtService {

    private final Map<String, SecretKey> keysByKid = new LinkedHashMap<>();
    private final String signingKid;
    private final String issuer;
    private final Duration accessTtl;

    public JwtService(JwtProperties properties) {
        for (JwtProperties.SigningKey key : properties.getSigningKeys()) {
            var secretKey = Keys.hmacShaKeyFor(key.getSecret().getBytes(StandardCharsets.UTF_8));
            keysByKid.put(key.getKid(), secretKey);
        }
        if (keysByKid.isEmpty()) {
            throw new IllegalStateException("At least one signing key must be configured");
        }
        this.signingKid = keysByKid.keySet().iterator().next();
        this.issuer = properties.getIssuer();
        this.accessTtl = Duration.ofMinutes(properties.getAccessTtlMinutes());
    }

    public String issueAccessToken(UUID userId, String displayName, String email, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(signingKid).and()
                .issuer(issuer)
                .subject(userId.toString())
                .claim("name", displayName)
                .claim("email", email)
                .claim("ver", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(keysByKid.get(signingKid), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .keyLocator(header -> {
                    String kid = header instanceof JwsHeader jws ? jws.getKeyId() : null;
                    Key key = kid == null ? null : keysByKid.get(kid);
                    if (key == null) {
                        throw new JwtException("Unknown key id");
                    }
                    return key;
                })
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
