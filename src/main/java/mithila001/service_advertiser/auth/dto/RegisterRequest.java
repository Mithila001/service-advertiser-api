package mithila001.service_advertiser.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(example = "{\"name\":\"Test User\",\"email\":\"user@example.com\",\"password\":\"ExamplePass123!\",\"phone\":\"+94771234567\"}")
public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 150, message = "Must be at most 150 characters") String name,
        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        @Size(max = 255, message = "Must be at most 255 characters") String email,
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "Password is required") @Size(min = 8, message = "Must be at least 8 characters") @mithila001.service_advertiser.auth.BcryptPasswordLength String password,
        @NotBlank @Size(max = 32)
        @jakarta.validation.constraints.Pattern(regexp = "(?=(?:[^0-9]*[0-9]){7,15}[^0-9]*$)\\+?[0-9 ()-]+", message = "Must contain 7 to 15 digits with optional leading +, spaces, parentheses or hyphens") String phone) {
    public RegisterRequest {
        phone = phone == null ? null : phone.trim();
    }
    @Override
    public String toString() {
        return "RegisterRequest[REDACTED]";
    }
}
