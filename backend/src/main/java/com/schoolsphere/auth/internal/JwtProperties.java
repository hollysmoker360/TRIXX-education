package com.schoolsphere.auth.internal;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT settings (spec 8.1); the first signing key signs, all keys verify so rotation is smooth. */
@ConfigurationProperties(prefix = "schoolsphere.security.jwt")
public class JwtProperties {

    private String issuer = "schoolsphere";
    private long accessTtlMinutes = 15;
    private long refreshTtlDays = 30;
    private List<SigningKey> signingKeys = new ArrayList<>();

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTtlMinutes() {
        return accessTtlMinutes;
    }

    public void setAccessTtlMinutes(long accessTtlMinutes) {
        this.accessTtlMinutes = accessTtlMinutes;
    }

    public long getRefreshTtlDays() {
        return refreshTtlDays;
    }

    public void setRefreshTtlDays(long refreshTtlDays) {
        this.refreshTtlDays = refreshTtlDays;
    }

    public List<SigningKey> getSigningKeys() {
        return signingKeys;
    }

    public void setSigningKeys(List<SigningKey> signingKeys) {
        this.signingKeys = signingKeys;
    }

    public static class SigningKey {

        private String kid;
        private String secret;

        public String getKid() {
            return kid;
        }

        public void setKid(String kid) {
            this.kid = kid;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }
}
