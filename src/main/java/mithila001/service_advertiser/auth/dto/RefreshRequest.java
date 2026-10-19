package mithila001.service_advertiser.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Use the latest refreshToken from login or refresh, not the access JWT.")
public record RefreshRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}", message = "Must be a valid refresh token") String refreshToken) {
    @Override
    public String toString() {
        return "RefreshRequest[REDACTED]";
    }
}
