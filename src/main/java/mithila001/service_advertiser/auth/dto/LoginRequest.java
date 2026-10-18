package mithila001.service_advertiser.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(example = "{\"email\":\"user@example.com\",\"password\":\"ExamplePass123!\"}")
public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
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
