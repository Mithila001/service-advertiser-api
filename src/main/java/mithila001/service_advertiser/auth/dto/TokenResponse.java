package mithila001.service_advertiser.auth.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn,
                            String refreshToken, Instant refreshTokenExpiresAt) {
    @Override
    public String toString() {
        return "TokenResponse[REDACTED]";
    }
}
