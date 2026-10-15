package mithila001.service_advertiser.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import mithila001.service_advertiser.user.UserRole;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            try {
                if (Long.parseLong(jwt.getSubject()) <= 0 || jwt.getExpiresAt() == null
                        || !jwt.getExpiresAt().isAfter(clock.instant())
                        || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant())
                        || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                        || !jwt.getAudience().contains(properties.audience())) {
                    throw new IllegalArgumentException();
                }
                UserRole.valueOf(jwt.getClaimAsString("role"));
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException exception) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid access token", null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps, new JwtIssuerValidator(properties.issuer()), claims)));
        return decoder;
    }
}
