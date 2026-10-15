package mithila001.service_advertiser.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 150, message = "Must be at most 150 characters") String name,
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255, message = "Must be at most 255 characters") String email,
        @NotBlank(message = "Password is required") @Size(min = 8, message = "Must be at least 8 characters") String password) {
    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}
