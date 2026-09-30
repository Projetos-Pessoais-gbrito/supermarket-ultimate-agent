package com.supermarketagent.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque, rotating refresh tokens.
 *
 * <ul>
 *   <li>256 random bits, returned once; only the SHA-256 hash is stored.</li>
 *   <li>Every use rotates the token: the old one is revoked and a new one issued.</li>
 *   <li>A revoked token used again means it was copied: all of the user's sessions are revoked.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final JwtProperties properties;
    private final Clock clock;

    RefreshTokenService(JdbcTemplate jdbc, JwtProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId, hash(token), Timestamp.from(clock.instant().plus(properties.refreshTokenTtl())));
        return token;
    }

    /** Revokes the token and returns its user, so a new pair can be issued. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public long consume(String token) {
        List<StoredToken> found = jdbc.query("""
                SELECT id, user_id, expires_at, revoked_at FROM refresh_tokens
                WHERE token_hash = ? FOR UPDATE""",
                (rs, row) -> new StoredToken(rs.getLong("id"), rs.getLong("user_id"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at") != null),
                hash(token));
        if (found.isEmpty()) {
            throw new InvalidRefreshTokenException("Unknown refresh token");
        }
        StoredToken stored = found.getFirst();
        if (stored.revoked()) {
            // Only possible if someone kept a copy of an already rotated token
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", stored.userId());
            revokeAll(stored.userId());
            throw new InvalidRefreshTokenException("Refresh token was already used");
        }
        if (!stored.expiresAt().isAfter(clock.instant())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE id = ?", stored.id());
        return stored.userId();
    }

    /** Logout; unknown or already revoked tokens are ignored. */
    @Transactional
    public void revoke(String token) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE token_hash = ? AND revoked_at IS NULL",
                hash(token));
    }

    @Transactional
    public void revokeAll(long userId) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = now() WHERE user_id = ? AND revoked_at IS NULL", userId);
    }

    /** Keeps the table small; expired tokens are kept a week longer so reuse can still be detected. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
    @Transactional
    public void deleteExpired() {
        jdbc.update("DELETE FROM refresh_tokens WHERE expires_at < ?",
                Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private record StoredToken(long id, long userId, Instant expiresAt, boolean revoked) {
    }
}
