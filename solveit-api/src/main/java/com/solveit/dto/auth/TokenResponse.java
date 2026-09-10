package com.solveit.dto.auth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn) {
}
