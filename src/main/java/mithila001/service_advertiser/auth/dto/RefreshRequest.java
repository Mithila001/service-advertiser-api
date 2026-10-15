package mithila001.service_advertiser.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Must be a valid refresh token") String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[REDACTED]";
    }
}
