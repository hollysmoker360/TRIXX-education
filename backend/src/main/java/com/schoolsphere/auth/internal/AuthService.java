package com.schoolsphere.auth.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.auth.api.AuthTokens;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication: login, token refresh with rotation and reuse detection, registration, logout,
 * and password change (spec 8.1). Refresh tokens are opaque, hashed at rest, and single-use.
 */
@Service
public class AuthService implements UserDirectory, com.schoolsphere.auth.api.TokenVerifier {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Duration refreshTtl;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       AuditLogService auditLogService,
                       @Value("${schoolsphere.security.jwt.refresh-ttl-days}") long refreshTtlDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
    }

    @Transactional
    public LoginResult login(String email, String password, String deviceId, String devicePlatform) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim()).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditLogService.record("auth.login.failed");
            throw ApiException.unauthorized("invalid_credentials", "Email or password is incorrect.");
        }
        if (!"active".equals(user.getStatus())) {
            throw ApiException.forbidden("account_" + user.getStatus(), "This account is " + user.getStatus() + ".");
        }
        AuthTokens tokens = issueTokens(user, deviceId, UUID.randomUUID());
        auditLogService.record("auth.login", "app_user", user.getId(), null, null);
        return new LoginResult(tokens, toRef(user));
    }

    @Transactional
    public LoginResult register(String email, String password, String displayName, String phone,
                                String deviceId, String devicePlatform) {
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim()).isPresent()) {
            throw ApiException.conflict("email_taken", "An account with this email already exists.");
        }
        User user = new User();
        user.setEmail(email.trim());
        user.setDisplayName(displayName);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        AuthTokens tokens = issueTokens(user, deviceId, UUID.randomUUID());
        auditLogService.record("auth.register", "app_user", user.getId(), null, null);
        return new LoginResult(tokens, toRef(user));
    }

    @Transactional
    public AuthTokens refresh(String rawRefreshToken, String deviceId) {
        String hash = sha256(rawRefreshToken);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("invalid_token", "The refresh token is not valid."));

        if (token.getRevokedAt() != null || token.getRotatedAt() != null) {
            refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now(), "reuse_detected");
            auditLogService.record("auth.refresh.reuse_detected");
            throw ApiException.unauthorized("token_reuse",
                    "This token was already used. All sessions in the family were revoked for safety.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("token_expired", "The refresh token has expired.");
        }
        User user = userRepository.findById(token.getUserId())
                .filter(u -> u.getStatus().equals("active") && u.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.unauthorized("invalid_token", "The account is not available."));

        Instant now = Instant.now();
        RefreshToken next = newToken(user.getId(), token.getFamilyId(), deviceId);
        refreshTokenRepository.save(next);
        token.setRotatedAt(now);
        token.setReplacedById(next.getId());
        refreshTokenRepository.save(token);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getTokenVersion());
        return new AuthTokens(accessToken, next.rawToken, jwtService.accessTtlSeconds());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(sha256(rawRefreshToken)).ifPresent(token ->
                refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now(), "logout"));
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "User not found."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("wrong_password", "The current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.bumpTokenVersion();
        refreshTokenRepository.revokeAllForUser(userId, Instant.now(), "password_changed");
        auditLogService.record("auth.password.changed", "app_user", userId, null, null);
    }

    public UserRepository userRepository() {
        return userRepository;
    }

    private AuthTokens issueTokens(User user, String deviceId, UUID familyId) {
        RefreshToken token = newToken(user.getId(), familyId, deviceId);
        refreshTokenRepository.save(token);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getDisplayName(), user.getEmail(),
                user.getTokenVersion());
        return new AuthTokens(accessToken, token.rawToken, jwtService.accessTtlSeconds());
    }

    private RefreshToken newToken(UUID userId, UUID familyId, String deviceId) {
        String raw = newRandomToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setTokenHash(sha256(raw));
        token.setDeviceId(deviceId);
        token.setExpiresAt(Instant.now().plus(refreshTtl));
        token.rawToken = raw;
        return token;
    }

    private static String newRandomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UserDirectory.UserRef toRef(User user) {
        return new UserDirectory.UserRef(user.getId(), user.getEmail(), user.getDisplayName(), user.getPhone(),
                user.getStatus(), user.isPlatformAdmin());
    }

    public record LoginResult(AuthTokens tokens, UserDirectory.UserRef user) {
    }

    // --- UserDirectory (public API for other modules) ---

    @Override
    public Optional<UserRef> findById(UUID id) {
        return userRepository.findById(id).map(this::toRef);
    }

    @Override
    public Optional<UserRef> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).map(this::toRef);
    }

    @Override
    public List<UserRef> findAllByIdIn(List<UUID> ids) {
        return userRepository.findAllById(ids).stream().map(this::toRef).toList();
    }

    @Override
    public boolean isPlatformAdmin(UUID userId) {
        return userRepository.findById(userId).map(User::isPlatformAdmin).orElse(false);
    }

    @Override
    @Transactional
    public CreatedUser createUserWithTemporaryPassword(String email, String displayName, String phone) {
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim()).isPresent()) {
            throw ApiException.conflict("email_taken", "An account with this email already exists.");
        }
        String temporaryPassword = "Ss-" + newRandomToken().substring(0, 12);
        User user = new User();
        user.setEmail(email.trim());
        user.setDisplayName(displayName);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);
        return new CreatedUser(toRef(user), temporaryPassword);
    }

    // --- TokenVerifier (WebSocket handshake) ---

    @Override
    public java.util.Optional<com.schoolsphere.auth.api.TokenVerifier.VerifiedToken> verifyAccessToken(
            String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            io.jsonwebtoken.Claims claims = jwtService.parse(rawToken);
            UUID userId = UUID.fromString(claims.getSubject());
            Integer tokenVersion = claims.get("ver", Integer.class);
            return userRepository.findById(userId)
                    .filter(u -> u.getDeletedAt() == null && "active".equals(u.getStatus())
                            && (tokenVersion == null || tokenVersion == u.getTokenVersion()))
                    .map(u -> new com.schoolsphere.auth.api.TokenVerifier.VerifiedToken(u.getId(),
                            u.getDisplayName(), u.getEmail()));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }
}
