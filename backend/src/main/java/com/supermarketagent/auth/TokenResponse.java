package com.supermarketagent.auth;

/**
 * @param expiresIn        access token lifetime in seconds
 * @param refreshToken     single-use token for {@code POST /api/auth/refresh}
 * @param refreshExpiresIn refresh token lifetime in seconds
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, String refreshToken,
                            long refreshExpiresIn) {
}
