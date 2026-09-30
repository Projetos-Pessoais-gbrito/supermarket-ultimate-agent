package com.supermarketagent.auth;

/** Unknown, expired, revoked or reused refresh token: the user must log in again. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
