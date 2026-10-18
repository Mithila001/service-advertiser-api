package mithila001.service_advertiser.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Session tokens. accessToken is the JWT for Authorization; expiresIn is its lifetime in seconds. tokenType is Bearer. refreshToken is opaque and rotates on refresh. refreshTokenExpiresAt is the fixed session expiry in UTC.")
public record TokenResponse(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, Instant refreshTokenExpiresAt) {
    @Override
    public String toString() {
        return "TokenResponse[REDACTED]";
    }
}
