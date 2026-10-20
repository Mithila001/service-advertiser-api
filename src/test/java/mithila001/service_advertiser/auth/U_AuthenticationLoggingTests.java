package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import mithila001.service_advertiser.auth.dto.LoginRequest;
import mithila001.service_advertiser.user.*;

class U_AuthenticationLoggingTests {
    @Test void loginFailureAndRefreshReplayDoNotLogCredentials() {
        var users = mock(UserRepository.class);
        var tokens = mock(RefreshTokenRepository.class);
        var passwords = mock(PasswordEncoder.class);
        Instant now = Instant.parse("2026-10-20T00:00:00Z");
        var properties = new AuthProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "issuer", "audience",
                Duration.ofMinutes(15), Duration.ofDays(7));
        var service = new AuthenticationService(users, tokens, passwords, mock(JwtEncoder.class), properties,
                Clock.fixed(now, ZoneOffset.UTC));
        Logger logger = (Logger) LoggerFactory.getLogger(AuthenticationService.class);
        var capture = new ListAppender<ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        try {
            when(users.findByEmail(any())).thenReturn(Optional.empty());
            assertThrows(BadCredentialsException.class,
                    () -> service.login(new LoginRequest("secret-email@example.com", "secret-password")));
            User user = new User();
            user.setId(7L);
            user.setStatus(UserStatus.ACTIVE);
            UUID family = UUID.randomUUID();
            var token = new RefreshToken(7L, family, "secret-hash", now.minusSeconds(30), now.plusSeconds(300));
            token.markUsed(now.minusSeconds(10));
            when(tokens.findUserIdByTokenHash(any())).thenReturn(Optional.of(7L));
            when(users.findForUpdate(7L)).thenReturn(Optional.of(user));
            when(tokens.findByTokenHash(any())).thenReturn(Optional.of(token));
            assertThrows(InvalidRefreshTokenException.class, () -> service.refresh("secret-refresh-token"));
            verify(tokens).revokeFamily(family, now);
            assertEquals(2, capture.list.size());
            assertTrue(capture.list.stream().allMatch(e -> e.getLevel() == Level.WARN && e.getThrowableProxy() == null));
            assertTrue(capture.list.getLast().getKeyValuePairs().stream()
                    .anyMatch(p -> p.key.equals("reason") && p.value.equals("token_replay")));
            assertTrue(capture.list.stream().noneMatch(e ->
                    (e.getFormattedMessage() + e.getKeyValuePairs()).contains("secret-")));
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
