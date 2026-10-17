package com.schoolsphere.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Access-token issuing/verifying and key rotation (spec 8.1). */
class JwtServiceTest {

    private static final String SECRET_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SECRET_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private static JwtService service(String... secrets) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("schoolsphere");
        properties.setAccessTtlMinutes(15);
        List<JwtProperties.SigningKey> keys = new ArrayList<>();
        char kid = 'a';
        for (String secret : secrets) {
            JwtProperties.SigningKey key = new JwtProperties.SigningKey();
            key.setKid("k-" + kid++);
            key.setSecret(secret);
            keys.add(key);
        }
        properties.setSigningKeys(keys);
        return new JwtService(properties);
    }

    private static JwtService serviceWithKid(String kid, String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("schoolsphere");
        properties.setAccessTtlMinutes(15);
        JwtProperties.SigningKey key = new JwtProperties.SigningKey();
        key.setKid(kid);
        key.setSecret(secret);
        properties.setSigningKeys(List.of(key));
        return new JwtService(properties);
    }

    @Test
    void issuesAndParsesTokenWithExpectedClaims() {
        JwtService jwt = service(SECRET_A);
        UUID userId = UUID.randomUUID();

        String token = jwt.issueAccessToken(userId, "Grace Wanjiru", "grace@example.ac.ke", 4);
        Claims claims = jwt.parse(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.getIssuer()).isEqualTo("schoolsphere");
        assertThat(claims.get("name")).isEqualTo("Grace Wanjiru");
        assertThat(claims.get("email")).isEqualTo("grace@example.ac.ke");
        assertThat(claims.get("ver", Integer.class)).isEqualTo(4);
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
        assertThat(jwt.accessTtlSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void signsWithFirstKeyAndAcceptsEveryConfiguredKey() {
        // Rotation: 'k-b' added to a service that still signs with the first key 'k-a'.
        JwtService rotating = service(SECRET_A, SECRET_B);
        String fromFirst = rotating.issueAccessToken(UUID.randomUUID(), "A", "a@x", 0);
        assertThat(headerKid(fromFirst)).isEqualTo("k-a");

        // A token signed by the previous (still trusted) key verifies because all keys verify.
        JwtService previous = serviceWithKid("k-b", SECRET_B);
        String fromPrevious = previous.issueAccessToken(UUID.randomUUID(), "B", "b@x", 0);
        assertThat(headerKid(fromPrevious)).isEqualTo("k-b");
        assertThat(rotating.parse(fromPrevious).get("name")).isEqualTo("B");
    }

    @Test
    void rejectsTokenSignedByUnknownKey() {
        JwtService validator = service(SECRET_A);
        String foreign = service(SECRET_B).issueAccessToken(UUID.randomUUID(), "x", "x@x", 0);

        assertThatThrownBy(() -> validator.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromDifferentIssuer() {
        JwtProperties properties = new JwtProperties();
        properties.setIssuer("somewhere-else");
        JwtProperties.SigningKey key = new JwtProperties.SigningKey();
        key.setKid("k-a");
        key.setSecret(SECRET_A);
        properties.setSigningKeys(List.of(key));

        String token = new JwtService(properties).issueAccessToken(UUID.randomUUID(), "x", "x@x", 0);
        assertThatThrownBy(() -> service(SECRET_A).parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void requiresAtLeastOneSigningKey() {
        assertThatThrownBy(() -> service()).isInstanceOf(IllegalStateException.class);
    }

    private static String headerKid(String token) {
        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]));
        return header.replaceAll(".*\"kid\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
