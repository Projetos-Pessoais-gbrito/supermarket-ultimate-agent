package com.supermarketagent.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

    private final UserService users;
    private final AccountService accounts;

    MeController(UserService users, AccountService accounts) {
        this.users = users;
        this.accounts = accounts;
    }

    @GetMapping("/api/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        User user = users.get(Long.parseLong(jwt.getSubject()));
        return new MeResponse(user.getId(), user.getEmail());
    }

    /** LGPD data portability: everything the app keeps about the user, as a JSON download. */
    @GetMapping("/api/me/export")
    ResponseEntity<AccountExport> export(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("meus-dados.json").build().toString())
                .body(accounts.export(Long.parseLong(jwt.getSubject())));
    }

    /** LGPD right to erasure; the password is asked again because this cannot be undone. */
    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        accounts.delete(Long.parseLong(jwt.getSubject()), request.password());
    }

    record MeResponse(long id, String email) {
    }

    record DeleteAccountRequest(@NotBlank String password) {
    }
}
