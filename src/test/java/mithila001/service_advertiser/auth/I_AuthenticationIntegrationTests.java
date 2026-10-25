package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.auth.dto.LoginRequest;
import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.TokenResponse;

@SpringBootTest
@AutoConfigureMockMvc
@Import(I_AuthenticationIntegrationTests.TestEndpoints.class)
class I_AuthenticationIntegrationTests extends PostgresTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");
    private static final String PASSWORD = " SecurePass123! ";
    @Autowired AuthenticationService service;
    @Autowired RegistrationService registration;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;
    @MockitoBean Clock clock;
    @MockitoSpyBean JwtEncoder encoder;

    @BeforeEach
    void resetClock() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void loginReturnsTokensAndBearerIdentityWithoutPersistingRawTokens() throws Exception {
        String email = register();
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        TokenResponse response = mapper.readValue(body, TokenResponse.class);
        assertEquals(NOW.plus(Duration.ofDays(7)), response.refreshTokenExpiresAt());
        assertTrue(response.refreshToken().matches("[A-Za-z0-9_-]{43}"));
        String hash = jdbc.queryForObject("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, userId(email));
        assertEquals(64, hash.length());
        assertNotEquals(response.refreshToken(), hash);
        assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(response.refreshToken().getBytes(java.nio.charset.StandardCharsets.UTF_8))), hash);
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId(email).toString()))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"));
        mvc.perform(get("/test/admin").header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/test/identity")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer")).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        assertFalse(response.toString().contains(response.refreshToken()));
    }

    @Test
    void loginAndRefreshTokensSurviveSmallClockRollbackButKeepValidationBoundaries() throws Exception {
        String email = register();
        when(clock.instant()).thenReturn(NOW.plusMillis(500));
        TokenResponse first = login(email);
        TokenResponse refreshed = service.refresh(first.refreshToken());
        when(clock.instant()).thenReturn(NOW.minusMillis(250));
        for (String token : new String[] {first.accessToken(), refreshed.accessToken()}) {
            mvc.perform(get("/test/identity").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId(email).toString()));
        }
        when(clock.instant()).thenReturn(NOW.minusSeconds(61));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + refreshed.accessToken()))
                .andExpect(status().isUnauthorized());
        when(clock.instant()).thenReturn(NOW.plusSeconds(900));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + refreshed.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidCredentialsAndInactiveAccountsShareTheSameError() throws Exception {
        String email = register();
        String expected = loginError(email, "wrong-password");
        assertEquals(expected, loginError(UUID.randomUUID() + "@example.com", PASSWORD));
        assertEquals(expected, loginError(email, PASSWORD.trim()));
        for (String status : new String[] { "SUSPENDED", "DISABLED" }) {
            jdbc.update("UPDATE users SET status = ? WHERE email = ?", status, email);
            assertEquals(expected, loginError(email, PASSWORD));
        }
        assertEquals(0, count(email));
    }

    @Test
    void validatesRequestsAndKeepsAuthenticationErrorsGeneric() throws Exception {
        for (String endpoint : new String[] { "login", "refresh", "logout" }) {
            mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request data"));
            mvc.perform(post("/api/v1/auth/" + endpoint).contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Malformed JSON request"));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "not-an-email", "password", PASSWORD))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "invalid"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "a".repeat(43)))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void refreshWorksAfterAccessExpiryAndInheritsSessionDeadline() throws Exception {
        String email = register();
        TokenResponse first = login(email);
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(16)));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isUnauthorized());
        String body = mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        TokenResponse next = mapper.readValue(body, TokenResponse.class);
        assertNotEquals(first.refreshToken(), next.refreshToken());
        assertTrue(next.refreshToken().matches("[A-Za-z0-9_-]{43}"));
        assertNotEquals(first.accessToken(), next.accessToken());
        assertEquals(first.refreshTokenExpiresAt(), next.refreshTokenExpiresAt());
        assertEquals(userId(email).toString(), decoder.decode(next.accessToken()).getSubject());
        assertEquals(2, count(email));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND used_at IS NOT NULL", Integer.class, userId(email)));
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", first.refreshToken()))))
                .andExpect(status().isUnauthorized());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(next.refreshToken()));
    }

    @Test
    void rotatesRefreshTokensWithoutExtendingOriginalExpiry() {
        String email = register();
        TokenResponse first = login(email);
        TokenResponse otherDevice = login(email);
        TokenResponse current = first;
        for (long minutes : new long[] { 16, 32, 7 * 24 * 60 - 1 }) {
            when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(minutes)));
            TokenResponse next = service.refresh(current.refreshToken());
            assertNotEquals(current.refreshToken(), next.refreshToken());
            assertEquals(first.refreshTokenExpiresAt(), next.refreshTokenExpiresAt());
            current = next;
        }
        assertEquals(5, count(email));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NOT NULL", Integer.class, userId(email)));
        assertNotNull(service.refresh(otherDevice.refreshToken()));
        when(clock.instant()).thenReturn(first.refreshTokenExpiresAt());
        String expiredToken = current.refreshToken();
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(expiredToken));
    }

    @Test
    void replayRevokesRotatedDescendantsButPreservesOtherSessions() {
        String email = register();
        TokenResponse first = login(email);
        TokenResponse otherDevice = login(email);
        TokenResponse next = service.refresh(first.refreshToken());
        TokenResponse latest = service.refresh(next.refreshToken());
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(first.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(latest.refreshToken()));
        assertNotNull(service.refresh(otherDevice.refreshToken()));
    }

    @Test
    void logoutIsIdempotentAndRevokesOnlyItsSessionWhileAccessJwtRemainsUsable() throws Exception {
        String email = register();
        TokenResponse first = login(email);
        TokenResponse other = login(email);
        TokenResponse rotated = service.refresh(first.refreshToken());
        for (String token : new String[] { first.refreshToken(), first.refreshToken(), "a".repeat(43) }) {
            mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("refreshToken", token))))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(first.refreshToken()));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(rotated.refreshToken()));
        mvc.perform(get("/test/identity").header("Authorization", "Bearer " + first.accessToken()))
                .andExpect(status().isOk());
        assertNotNull(service.refresh(other.refreshToken()));
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(16)));
        mvc.perform(post("/api/v1/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", other.refreshToken()))))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshUsesCurrentRoleAndRevokesSessionForInactiveAccount() {
        String email = register();
        TokenResponse first = login(email);
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'PROVIDER') WHERE email = ?", email);
        TokenResponse next = service.refresh(first.refreshToken());
        assertEquals("PROVIDER", decoder.decode(next.accessToken()).getClaimAsString("role"));
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE email = ?", email);
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(next.refreshToken()));
        jdbc.update("UPDATE users SET status = 'ACTIVE' WHERE email = ?", email);
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(next.refreshToken()));
    }

    @Test
    void expiryAndCleanupPreserveActiveSessions() {
        String email = register();
        TokenResponse expired = login(email);
        TokenResponse expiredNext = service.refresh(expired.refreshToken());
        when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(7)));
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(expiredNext.refreshToken()));
        TokenResponse active = login(email);
        TokenResponse activeNext = service.refresh(active.refreshToken());
        service.deleteExpiredTokens();
        assertEquals(2, count(email));
        assertNotEquals(activeNext.refreshToken(), service.refresh(activeNext.refreshToken()).refreshToken());
    }

    @Test
    void signingFailureLeavesRefreshTokenUsable() {
        String email = register();
        TokenResponse first = login(email);
        doThrow(new JwtEncodingException("Test signing failure")).when(encoder).encode(any());
        assertThrows(JwtEncodingException.class, () -> service.refresh(first.refreshToken()));
        assertEquals(1, count(email));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND used_at IS NOT NULL", Integer.class, userId(email)));
        org.mockito.Mockito.doCallRealMethod().when(encoder).encode(any());
        assertNotEquals(first.refreshToken(), service.refresh(first.refreshToken()).refreshToken());
    }

    @Test
    void concurrentRefreshOnlySucceedsOnceAndReplayRevokesSession() throws Exception {
        String email = register();
        TokenResponse first = login(email);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        java.util.concurrent.Callable<TokenResponse> refresh = () -> {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                return service.refresh(first.refreshToken());
            } catch (InvalidRefreshTokenException exception) {
                return null;
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = executor.submit(refresh);
            var right = executor.submit(refresh);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            TokenResponse a = left.get(15, TimeUnit.SECONDS);
            TokenResponse b = right.get(15, TimeUnit.SECONDS);
            assertTrue((a == null) != (b == null), "Exactly one refresh must succeed");
            TokenResponse winner = a == null ? b : a;
            assertNotEquals(first.refreshToken(), winner.refreshToken());
            assertEquals(first.refreshTokenExpiresAt(), winner.refreshTokenExpiresAt());
            assertEquals(2, count(email));
            assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(winner.refreshToken()));
        }
    }

    @Test
    void tokensConsumedBeforeThisChangeRemainInvalid() {
        String email = register();
        TokenResponse old = login(email);
        jdbc.update("UPDATE refresh_tokens SET used_at = created_at WHERE user_id = ?", userId(email));
        TokenResponse current = login(email);
        assertThrows(InvalidRefreshTokenException.class, () -> service.refresh(old.refreshToken()));
        assertNotEquals(current.refreshToken(), service.refresh(current.refreshToken()).refreshToken());
    }

    private String register() {
        String email = UUID.randomUUID() + "@example.com";
        registration.register(new RegisterRequest("Auth Test", email, PASSWORD));
        return email;
    }

    private TokenResponse login(String email) {
        return service.login(new LoginRequest(email, PASSWORD));
    }

    private Long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private int count(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, userId(email));
    }

    private String loginError(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid email or password"))
                .andReturn().getResponse().getContentAsString();
    }

    private String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    @RestController
    public static class TestEndpoints {
        @GetMapping("/test/identity")
        public Map<String, String> identity(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
            return Map.of("id", jwt.getSubject(), "role", authentication.getAuthorities().iterator().next().getAuthority());
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/test/admin")
        public String admin() {
            return "admin";
        }
    }
}
