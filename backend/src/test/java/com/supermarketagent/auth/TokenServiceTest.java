package com.supermarketagent.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.supermarketagent.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class TokenServiceTest {

    private final SecurityConfig config = new SecurityConfig();
    private final JwtProperties properties =
            new JwtProperties("test-only-secret-with-at-least-32-characters", Duration.ofHours(1), "supermarket-agent");
    private final JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(properties), properties);

    @Test
    void issuesTokenWithUserIdAsSubjectAndOneHourExpiry() {
        TokenResponse response = service(Clock.systemUTC()).issueFor(user(42L));

        Jwt jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("supermarket-agent");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(jwt.getClaims()).doesNotContainKey("email");
    }

    @Test
    void expiredTokensAreRejected() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String token = service(twoHoursAgo).issueFor(user(42L)).accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void tokensFromAnotherIssuerAreRejected() {
        JwtProperties other = new JwtProperties(properties.secret(), Duration.ofHours(1), "someone-else");
        String token = new TokenService(config.jwtEncoder(config.jwtSigningKey(other)), other, Clock.systemUTC())
                .issueFor(user(42L)).accessToken();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private TokenService service(Clock clock) {
        return new TokenService(config.jwtEncoder(config.jwtSigningKey(properties)), properties, clock);
    }

    private static User user(long id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}
