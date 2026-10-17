package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class U_AuthConfigurationTests {
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private final AuthProperties properties = properties(KEY);
    private final AuthConfiguration config = new AuthConfiguration();
    private final JwtEncoder encoder = config.jwtEncoder(properties);
    private final JwtDecoder decoder = config.jwtDecoder(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void signedTokenHasIdentityAndRejectsTamperingAndWrongKey() {
        String token = token(Map.of());
        assertEquals("7", decoder.decode(token).getSubject());
        String[] parts = token.split("\\.");
        String signature = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> decoder.decode(parts[0] + "." + parts[1] + "." + signature));
        byte[] other = new byte[32];
        other[0] = 1;
        JwtDecoder otherDecoder = config.jwtDecoder(properties(Base64.getEncoder().encodeToString(other)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(JwtException.class, () -> otherDecoder.decode(token));
    }

    @Test
    void rejectsExpiredAndInvalidOrMissingClaims() {
        for (Map<String, Object> change : List.<Map<String, Object>>of(
                Map.of("exp", NOW.minusSeconds(1)), Map.of("exp", NOW),
                Map.of("iss", "wrong"), Map.of("aud", List.of("wrong")),
                Map.of("sub", "not-a-number"), Map.of("sub", "0"),
                Map.of("role", "SUPERUSER"), Map.of("iat", NOW.plusSeconds(1)))) {
            assertThrows(JwtException.class, () -> decoder.decode(token(change)), change.keySet().toString());
        }
        for (String missing : List.of("sub", "role", "exp", "iat", "aud", "iss")) {
            JwtClaimsSet claims = baseClaims().claims(values -> values.remove(missing)).build();
            assertThrows(JwtException.class, () -> decoder.decode(encode(claims)), missing);
        }
    }

    @Test
    void enforcesAlgorithmAndRejectsBadConfigurationWithoutExposingSecret() {
        assertThrows(JwtException.class, () -> decoder.decode("eyJhbGciOiJub25lIn0.eyJzdWIiOiI3In0."));
        for (String key : new String[] { "", "invalid-base64", Base64.getEncoder().encodeToString(new byte[16]) }) {
            assertThrows(IllegalArgumentException.class, () -> properties(key));
        }
        assertThrows(IllegalArgumentException.class, () -> properties(null));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(KEY, "issuer", "audience", Duration.ZERO, Duration.ofDays(7)));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(KEY, "issuer", "audience", Duration.ofMinutes(15), Duration.ofMinutes(1)));
        assertFalse(properties.toString().contains(KEY));
    }

    private JwtClaimsSet.Builder baseClaims() {
        return JwtClaimsSet.builder().subject("7").issuer(properties.issuer())
                .audience(List.of(properties.audience())).issuedAt(NOW.minusSeconds(10))
                .expiresAt(NOW.plusSeconds(900)).claim("role", "CUSTOMER");
    }

    private String token(Map<String, Object> changes) {
        return encode(baseClaims().claims(values -> values.putAll(changes)).build());
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static AuthProperties properties(String key) {
        return new AuthProperties(key, "service-advertiser", "service-advertiser-api", Duration.ofMinutes(15), Duration.ofDays(7));
    }
}
