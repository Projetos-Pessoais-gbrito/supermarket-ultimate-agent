package com.supermarketagent.auth;

import com.supermarketagent.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final UserService users;
    private final TokenService tokens;
    private final RefreshTokenService refreshTokens;
    private final LoginAttemptLimiter limiter;

    AuthController(UserService users, TokenService tokens, RefreshTokenService refreshTokens,
                   LoginAttemptLimiter limiter) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody CredentialsRequest request, HttpServletRequest http) {
        limiter.checkAndRecordRegistration(clientIp(http));
        return tokens.issueFor(users.register(request.email(), request.password()).getId());
    }

    /** Failures count per e-mail and per IP, for existing and unknown e-mails alike (ADR 0006). */
    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody CredentialsRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        limiter.checkLogin(request.email(), ip);
        try {
            long userId = users.authenticate(request.email(), request.password()).getId();
            limiter.recordLoginSuccess(request.email());
            return tokens.issueFor(userId);
        } catch (BadCredentialsException e) {
            limiter.recordLoginFailure(request.email(), ip);
            throw e;
        }
    }

    /**
     * The direct peer address. {@code X-Forwarded-For} is deliberately ignored: it is client-controlled
     * unless a trusted proxy sets it, and trusting it would let attackers rotate "IPs" freely.
     */
    private static String clientIp(HttpServletRequest http) {
        return http.getRemoteAddr();
    }

    /** Exchanges a refresh token for a new pair; the old refresh token stops working. */
    @PostMapping("/refresh")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return tokens.issueFor(refreshTokens.consume(request.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokens.revoke(request.refreshToken());
    }

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }
}
