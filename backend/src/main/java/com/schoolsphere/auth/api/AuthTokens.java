package com.schoolsphere.auth.api;

public record AuthTokens(String accessToken, String refreshToken, long expiresInSeconds) {
}
