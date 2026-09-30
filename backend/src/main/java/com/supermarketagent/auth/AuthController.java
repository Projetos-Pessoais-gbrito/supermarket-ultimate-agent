package com.supermarketagent.auth;

import com.supermarketagent.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

    AuthController(UserService users, TokenService tokens, RefreshTokenService refreshTokens) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody CredentialsRequest request) {
        return tokens.issueFor(users.register(request.email(), request.password()).getId());
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody CredentialsRequest request) {
        return tokens.issueFor(users.authenticate(request.email(), request.password()).getId());
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
