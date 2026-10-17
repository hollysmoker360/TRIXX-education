package com.schoolsphere.auth.web;

import com.schoolsphere.auth.api.AuthTokens;
import com.schoolsphere.auth.internal.AuthService;
import com.schoolsphere.auth.security.JwtAuthenticationFilter;
import com.schoolsphere.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest request,
                                         @RequestHeader(value = JwtAuthenticationFilter.DEVICE_HEADER, required = false)
                                         String deviceId) {
        return authService.login(request.email(), request.password(), deviceId, request.platform());
    }

    @PostMapping("/register")
    public AuthService.LoginResult register(@Valid @RequestBody RegisterRequest request,
                                            @RequestHeader(value = JwtAuthenticationFilter.DEVICE_HEADER, required = false)
                                            String deviceId) {
        return authService.register(request.email(), request.password(), request.displayName(),
                request.phone(), deviceId, request.platform());
    }

    @PostMapping("/refresh")
    public AuthTokens refresh(@Valid @RequestBody RefreshRequest request,
                              @RequestHeader(value = JwtAuthenticationFilter.DEVICE_HEADER, required = false)
                              String deviceId) {
        return authService.refresh(request.refreshToken(), deviceId);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return Map.of("ok", true);
    }

    @PostMapping("/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(SecurityUtils.requireUserId(), request.currentPassword(), request.newPassword());
        return Map.of("ok", true);
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password, String platform) {
    }

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 8, max = 100) String password,
                                  @NotBlank @Size(max = 120) String displayName,
                                  String phone,
                                  String platform) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }
}
