package mithila001.service_advertiser.auth;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String secretBase64, String issuer, String audience,
                             Duration accessLifetime, Duration refreshLifetime) {
    public AuthProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
        if (accessLifetime == null || accessLifetime.toSeconds() < 1
                || refreshLifetime == null || refreshLifetime.compareTo(accessLifetime) <= 0) {
            throw new IllegalArgumentException("Token lifetimes must be positive; refresh must outlive access");
        }
        try {
            if (secretBase64 == null || Base64.getDecoder().decode(secretBase64).length < 32) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must encode at least 32 random bytes");
        }
    }

    SecretKey signingKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256");
    }

    @Override
    public String toString() {
        return "AuthProperties[secretBase64=REDACTED]";
    }
}
