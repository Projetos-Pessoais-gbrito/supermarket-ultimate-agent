package com.supermarketagent.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues access tokens whose subject is the user id; no personal data goes into the token. */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final RefreshTokenService refreshTokens;
    private final Clock clock;

    @Autowired
    TokenService(JwtEncoder encoder, JwtProperties properties, RefreshTokenService refreshTokens) {
        this(encoder, properties, refreshTokens, Clock.systemUTC());
    }

    TokenService(JwtEncoder encoder, JwtProperties properties, RefreshTokenService refreshTokens, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    /** New access token plus a new refresh token (a new session, or the next step of a rotation). */
    public TokenResponse issueFor(long userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds(),
                refreshTokens.issue(userId), properties.refreshTokenTtl().toSeconds());
    }
}
