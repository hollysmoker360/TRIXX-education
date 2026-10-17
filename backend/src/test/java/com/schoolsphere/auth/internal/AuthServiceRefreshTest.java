package com.schoolsphere.auth.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Refresh-token rotation, reuse detection, and token-version invalidation (spec 8.1). The reuse
 * case is the security-critical one: a replayed refresh token must revoke the whole family.
 */
class AuthServiceRefreshTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AuditLogService auditLogService;
    private AuthService authService;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        auditLogService = mock(AuditLogService.class);
        passwordEncoder = new BCryptPasswordEncoder();

        // Mimic JPA @PrePersist: save() assigns an id to a new RefreshToken.
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
            }
            return token;
        });

        JwtProperties properties = new JwtProperties();
        properties.setIssuer("schoolsphere");
        properties.setAccessTtlMinutes(15);
        JwtProperties.SigningKey key = new JwtProperties.SigningKey();
        key.setKid("k-test");
        key.setSecret(SECRET);
        properties.setSigningKeys(List.of(key));

        authService = new AuthService(userRepository, refreshTokenRepository, new JwtService(properties),
                passwordEncoder, auditLogService, 30);
    }

    private User activeUser(String rawPassword) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("grace@kilimani.ac.ke");
        user.setDisplayName("Grace Wanjiru");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        return user;
    }

    private RefreshToken storedToken(User user, String rawToken, UUID familyId) {
        RefreshToken token = new RefreshToken();
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        token.setUserId(user.getId());
        token.setFamilyId(familyId);
        token.setTokenHash(AuthService.sha256(rawToken));
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    @Test
    void loginIssuesTokensForValidCredentials() {
        User user = activeUser("Demo1234!");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("grace@kilimani.ac.ke"))
                .thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.login("grace@kilimani.ac.ke", "Demo1234!", "device-1", "android");

        assertThat(result.tokens().accessToken()).isNotBlank();
        assertThat(result.tokens().refreshToken()).isNotBlank();
        assertThat(result.tokens().expiresInSeconds()).isEqualTo(900);
        assertThat(result.user().email()).isEqualTo("grace@kilimani.ac.ke");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsWrongPasswordWithoutLeakingWhichPartFailed() {
        User user = activeUser("Demo1234!");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("grace@kilimani.ac.ke", "wrong", "d", "android"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Email or password is incorrect.");
        verify(auditLogService).record("auth.login.failed");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesANewPair() {
        User user = activeUser("Demo1234!");
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(user, "raw-refresh-1", family);
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        com.schoolsphere.auth.api.AuthTokens tokens = authService.refresh("raw-refresh-1", "device-1");

        assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo("raw-refresh-1");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(stored.getRotatedAt()).isNotNull();
        assertThat(stored.getReplacedById()).isNotNull();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        verify(refreshTokenRepository, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        User user = activeUser("Demo1234!");
        UUID family = UUID.randomUUID();
        RefreshToken reused = storedToken(user, "raw-refresh-1", family);
        reused.setRotatedAt(Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> authService.refresh("raw-refresh-1", "attacker-device"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already used");

        verify(refreshTokenRepository).revokeFamily(eq(family), any(Instant.class), eq("reuse_detected"));
        verify(auditLogService).record("auth.refresh.reuse_detected");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void reusingARevokedTokenAlsoRevokesTheFamily() {
        User user = activeUser("Demo1234!");
        UUID family = UUID.randomUUID();
        RefreshToken revoked = storedToken(user, "raw-refresh-1", family);
        revoked.setRevokedAt(Instant.now().minusSeconds(5));
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh("raw-refresh-1", "device"))
                .isInstanceOf(ApiException.class);
        verify(refreshTokenRepository).revokeFamily(eq(family), any(Instant.class), eq("reuse_detected"));
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        User user = activeUser("Demo1234!");
        RefreshToken expired = storedToken(user, "raw-refresh-1", UUID.randomUUID());
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh("raw-refresh-1", "device"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("expired");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("not-a-real-token", "device"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    void suspendedAccountCannotRefresh() {
        User user = activeUser("Demo1234!");
        user.setStatus("suspended");
        RefreshToken stored = storedToken(user, "raw-refresh-1", UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refresh("raw-refresh-1", "device"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void logoutRevokesTheTokenFamily() {
        User user = activeUser("Demo1234!");
        UUID family = UUID.randomUUID();
        RefreshToken stored = storedToken(user, "raw-refresh-1", family);
        when(refreshTokenRepository.findByTokenHash(AuthService.sha256("raw-refresh-1")))
                .thenReturn(Optional.of(stored));

        authService.logout("raw-refresh-1");

        verify(refreshTokenRepository).revokeFamily(eq(family), any(Instant.class), eq("logout"));
    }

    @Test
    void changePasswordBumpsTokenVersionAndRevokesEveryToken() {
        User user = activeUser("Demo1234!");
        int before = user.getTokenVersion();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.changePassword(user.getId(), "Demo1234!", "NewPassword!23");

        assertThat(user.getTokenVersion()).isEqualTo(before + 1);
        assertThat(passwordEncoder.matches("NewPassword!23", user.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(eq(user.getId()), any(Instant.class), eq("password_changed"));
        verify(auditLogService).record(eq("auth.password.changed"), eq("app_user"), eq(user.getId()), any(), any());
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        User user = activeUser("Demo1234!");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.changePassword(user.getId(), "nope", "NewPassword!23"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("current password is incorrect");
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any(), any());
    }
}
