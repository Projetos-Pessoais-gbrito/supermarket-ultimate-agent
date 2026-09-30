package com.supermarketagent.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}
