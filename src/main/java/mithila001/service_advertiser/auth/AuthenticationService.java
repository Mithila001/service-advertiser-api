package mithila001.service_advertiser.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import mithila001.service_advertiser.auth.dto.LoginRequest;
import mithila001.service_advertiser.auth.dto.TokenResponse;
import mithila001.service_advertiser.user.User;
import mithila001.service_advertiser.user.UserRepository;
import mithila001.service_advertiser.user.UserStatus;

@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthenticationService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwords,
                                 JwtEncoder encoder, AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (!matches || user == null || user.getStatus() != UserStatus.ACTIVE) {
            log.atWarn().addKeyValue("event", "login_failure")
                    .addKeyValue("reason", "invalid_credentials_or_inactive").log("Authentication rejected");
            throw new BadCredentialsException("Invalid email or password");
        }
        Instant now = clock.instant();
        Instant refreshExpiry = now.plus(properties.refreshLifetime());
        return issue(user, UUID.randomUUID(), now, refreshExpiry);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawToken) {
        String hash = hash(rawToken);
        Long userId = refreshTokens.findUserIdByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        // ponytail: serialize this user's refresh/logout operations; lock per session if contention grows.
        User user = users.findForUpdate(userId).orElseThrow(InvalidRefreshTokenException::new);
        RefreshToken token = find(hash).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        if (token.getUsedAt() != null || user.getStatus() != UserStatus.ACTIVE) {
            revoke(token.getFamilyId(), now);
            log.atWarn().addKeyValue("event", "refresh_security")
                    .addKeyValue("reason", token.getUsedAt() != null ? "token_replay" : "inactive_account")
                    .addKeyValue("user_id", userId).addKeyValue("family_id", token.getFamilyId())
                    .log("Refresh family revocation requested");
            // Commit replay/inactive-account revocation even though the API returns 401.
            throw new InvalidRefreshTokenException();
        }
        token.markUsed(now);
        return issue(user, token.getFamilyId(), now, token.getExpiresAt());
    }

    @Transactional
    public void logout(String rawToken) {
        Optional<RefreshToken> initial = find(hash(rawToken));
        if (initial.isPresent() && users.findForUpdate(initial.get().getUserId()).isPresent()) {
            revoke(initial.get().getFamilyId(), clock.instant());
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void deleteExpiredTokens() {
        refreshTokens.deleteExpired(clock.instant());
    }

    private TokenResponse issue(User user, UUID familyId, Instant now, Instant refreshExpiry) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(user.getId(), familyId, hash(refreshToken), now, refreshExpiry));
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(user.getId().toString())
                .issuer(properties.issuer()).audience(java.util.List.of(properties.audience()))
                .issuedAt(now).expiresAt(now.plus(properties.accessLifetime()))
                .claim("role", user.getRole().getName().name()).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new TokenResponse(accessToken, "Bearer", properties.accessLifetime().toSeconds(), refreshToken, refreshExpiry);
    }

    private Optional<RefreshToken> find(String hash) {
        return refreshTokens.findByTokenHash(hash);
    }

    private void revoke(UUID familyId, Instant now) {
        refreshTokens.revokeFamily(familyId, now);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
