package com.supermarketagent.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Sliding-window counters against password guessing and registration spam (ADR 0006).
 *
 * <p>Failures are counted per e-mail and per client IP whether or not the e-mail exists, so the
 * answer never reveals registered accounts. State lives in memory: fine for a single instance.
 */
@Component
@EnableConfigurationProperties(LoginAttemptProperties.class)
public class LoginAttemptLimiter {

    /** Upper bound on tracked keys, so an attacker cycling e-mails cannot exhaust memory. */
    static final int MAX_TRACKED_KEYS = 100_000;

    private final LoginAttemptProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    LoginAttemptLimiter(LoginAttemptProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** @throws TooManyAttemptsException when the e-mail or the IP already failed too often */
    public void checkLogin(String email, String clientIp) {
        check(emailKey(email), properties.maxFailuresPerEmail());
        check(ipKey(clientIp), properties.maxFailuresPerIp());
    }

    public void recordLoginFailure(String email, String clientIp) {
        record(emailKey(email));
        record(ipKey(clientIp));
    }

    /** The owner got in: forget the e-mail's failures (the IP keeps its count). */
    public void recordLoginSuccess(String email) {
        attempts.remove(emailKey(email));
    }

    /** Counts every registration attempt per IP, successful or not. */
    public void checkAndRecordRegistration(String clientIp) {
        String key = "register:" + clientIp;
        check(key, properties.maxRegistrationsPerIp());
        record(key);
    }

    void clear() {
        attempts.clear();
    }

    private void check(String key, int max) {
        Deque<Instant> recent = attempts.get(key);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            prune(recent);
            if (recent.size() >= max) {
                Duration retryAfter = Duration.between(clock.instant(), recent.peekFirst().plus(properties.window()));
                throw new TooManyAttemptsException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
            }
        }
    }

    private void record(String key) {
        if (attempts.size() >= MAX_TRACKED_KEYS) {
            evictExpired();
        }
        Deque<Instant> recent = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            prune(recent);
            recent.addLast(clock.instant());
        }
    }

    private void prune(Deque<Instant> recent) {
        Instant limit = clock.instant().minus(properties.window());
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(limit)) {
            recent.pollFirst();
        }
    }

    private void evictExpired() {
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                prune(entry.getValue());
                return entry.getValue().isEmpty();
            }
        });
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + clientIp;
    }
}
