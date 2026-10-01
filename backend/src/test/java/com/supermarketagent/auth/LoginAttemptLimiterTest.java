package com.supermarketagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-30T12:00:00Z"));
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(new LoginAttemptProperties(3, 5, 2, Duration.ofMinutes(15)), clock);

    @Test
    void blocksAnEmailAfterTooManyFailures() {
        failLogins("ana@example.com", "10.0.0.1", 3);

        assertThatThrownBy(() -> limiter.checkLogin("ana@example.com", "10.0.0.2"))
                .isInstanceOf(TooManyAttemptsException.class)
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).retryAfter()).isEqualTo(Duration.ofMinutes(15)));
    }

    @Test
    void normalizesTheEmail() {
        failLogins(" ANA@Example.com ", "10.0.0.1", 3);

        assertThatThrownBy(() -> limiter.checkLogin("ana@example.com", "10.0.0.2"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void doesNotAffectOtherEmails() {
        failLogins("ana@example.com", "10.0.0.1", 3);

        assertThatCode(() -> limiter.checkLogin("bia@example.com", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void blocksAnIpTryingManyEmails() {
        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure("user" + i + "@example.com", "10.0.0.9");
        }

        assertThatThrownBy(() -> limiter.checkLogin("new@example.com", "10.0.0.9"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void successfulLoginResetsTheEmailCounter() {
        failLogins("ana@example.com", "10.0.0.1", 2);
        limiter.recordLoginSuccess("ana@example.com");
        failLogins("ana@example.com", "10.0.0.1", 2);

        assertThatCode(() -> limiter.checkLogin("ana@example.com", "10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void failuresExpireAfterTheWindow() {
        failLogins("ana@example.com", "10.0.0.1", 3);
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThatCode(() -> limiter.checkLogin("ana@example.com", "10.0.0.1")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        failLogins("ana@example.com", "10.0.0.1", 3);
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> limiter.checkLogin("ana@example.com", "10.0.0.1"))
                .satisfies(e -> assertThat(((TooManyAttemptsException) e).retryAfter()).isEqualTo(Duration.ofMinutes(5)));
    }

    @Test
    void limitsRegistrationsPerIp() {
        limiter.checkAndRecordRegistration("10.0.0.1");
        limiter.checkAndRecordRegistration("10.0.0.1");

        assertThatThrownBy(() -> limiter.checkAndRecordRegistration("10.0.0.1"))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.checkAndRecordRegistration("10.0.0.2")).doesNotThrowAnyException();
    }

    private void failLogins(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.checkLogin(email, ip);
            limiter.recordLoginFailure(email, ip);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
