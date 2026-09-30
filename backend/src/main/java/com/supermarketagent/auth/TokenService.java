package com.supermarketagent.auth;

import com.supermarketagent.user.User;
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
    private final Clock clock;

    @Autowired
    TokenService(JwtEncoder encoder, JwtProperties properties) {
        this(encoder, properties, Clock.systemUTC());
    }

    TokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issueFor(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }
}
