package com.supermarketagent.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public record CredentialsRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /** BCrypt accepts at most 72 bytes; accented characters take 2 bytes in UTF-8. */
    static final int MAX_PASSWORD_BYTES = 72;

    public CredentialsRequest {
        // Tolerate spaces pasted around the e-mail; the password is kept exactly as typed
        email = email == null ? null : email.strip();
    }

    @AssertTrue(message = "password must be at most 72 bytes")
    boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES;
    }
}
