package com.supermarketagent.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final UserService users;

    MeController(UserService users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.get(Long.parseLong(jwt.getSubject()));
        return new MeResponse(user.getId(), user.getEmail());
    }

    record MeResponse(long id, String email) {
    }
}
