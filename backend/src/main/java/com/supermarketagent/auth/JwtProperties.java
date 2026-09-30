package com.supermarketagent.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret         HMAC key for signing tokens, at least 32 characters (env {@code JWT_SECRET})
 * @param accessTokenTtl how long an access token is valid
 * @param issuer         {@code iss} claim written and required on every token
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "set JWT_SECRET (at least 32 characters)")
        @Size(min = 32, message = "JWT_SECRET must have at least 32 characters")
        String secret,
        @DefaultValue("1h") Duration accessTokenTtl,
        @DefaultValue("supermarket-agent") String issuer) {
}
