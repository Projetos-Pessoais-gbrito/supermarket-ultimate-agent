package com.supermarketagent.auth;

import java.time.Duration;

/** Too many login or registration attempts; answered with 429 and {@code Retry-After}. */
public class TooManyAttemptsException extends RuntimeException {

    private final Duration retryAfter;

    public TooManyAttemptsException(Duration retryAfter) {
        super("Too many attempts, try again later");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
