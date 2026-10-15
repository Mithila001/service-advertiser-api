package mithila001.service_advertiser.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 1024) String password) {
    public LoginRequest {
        if (email != null) {
            email = email.trim();
        }
    }

    @Override
    public String toString() {
        return "LoginRequest[REDACTED]";
    }
}
