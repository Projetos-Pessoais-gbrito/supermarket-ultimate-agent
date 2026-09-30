package com.supermarketagent.auth;

import com.supermarketagent.user.UserService;
import jakarta.validation.Valid;
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

    AuthController(UserService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody CredentialsRequest request) {
        return tokens.issueFor(users.register(request.email(), request.password()));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody CredentialsRequest request) {
        return tokens.issueFor(users.authenticate(request.email(), request.password()));
    }
}
