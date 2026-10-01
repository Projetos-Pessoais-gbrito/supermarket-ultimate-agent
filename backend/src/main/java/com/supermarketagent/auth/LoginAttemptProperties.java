package com.supermarketagent.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brute-force protection for login and registration (ADR 0006).
 *
 * @param maxFailuresPerEmail  failed logins allowed per e-mail within {@code window}
 * @param maxFailuresPerIp     failed logins allowed per client IP within {@code window}
 * @param maxRegistrationsPerIp registration attempts allowed per client IP within {@code window}
 * @param window               sliding window the attempts are counted in
 */
@ConfigurationProperties("app.security.login-attempts")
public record LoginAttemptProperties(
        @DefaultValue("5") int maxFailuresPerEmail,
        @DefaultValue("20") int maxFailuresPerIp,
        @DefaultValue("10") int maxRegistrationsPerIp,
        @DefaultValue("15m") Duration window) {
}
