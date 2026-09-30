package com.supermarketagent.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredentialsRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // BCrypt only uses the first 72 bytes
        @NotBlank @Size(min = 8, max = 72) String password) {

    public CredentialsRequest {
        // Tolerate spaces pasted around the e-mail; the password is kept exactly as typed
        email = email == null ? null : email.strip();
    }
}
