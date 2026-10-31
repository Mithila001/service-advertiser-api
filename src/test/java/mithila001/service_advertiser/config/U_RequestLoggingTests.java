package mithila001.service_advertiser.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.auth.*;
import mithila001.service_advertiser.auth.dto.TokenResponse;
import mithila001.service_advertiser.business.BusinessController;
import mithila001.service_advertiser.business.BusinessService;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler;

@WebMvcTest(controllers = {AuthController.class, BusinessController.class,
        mithila001.service_advertiser.business.BusinessModerationController.class})
@Import({SecurityConfig.class, RequestLoggingFilter.class, GlobalExceptionHandler.class, U_RequestLoggingTests.Probe.class})
class U_RequestLoggingTests {
    @Autowired MockMvc mvc;
    @MockitoBean AuthenticationService authentication;
    @MockitoBean RegistrationService registration;
    @MockitoBean BusinessService businesses;
    @MockitoBean mithila001.service_advertiser.business.BusinessModerationService moderation;
    @MockitoBean JwtDecoder decoder;
    private final Logger logger = (Logger) LoggerFactory.getLogger("mithila001.service_advertiser");
    private final ListAppender<ILoggingEvent> capture = new ListAppender<>() {
        @Override protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @BeforeEach void captureLogs() { capture.start(); logger.addAppender(capture); MDC.clear(); }
    @AfterEach void cleanup() { logger.detachAppender(capture); capture.stop(); MDC.clear(); }

    private List<ILoggingEvent> events(String name) {
        return capture.list.stream().filter(e -> e.getKeyValuePairs() != null && e.getKeyValuePairs().stream()
                .anyMatch(pair -> pair.key.equals("event") && pair.value.equals(name))).toList();
    }

    private Object field(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream().filter(p -> p.key.equals(key)).findFirst().orElseThrow().value;
    }

    @Test void securityRejectionHasOneSummaryAndRestoresContext() throws Exception {
        when(decoder.decode(any())).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("Invalid token"));
        MDC.setContextMap(Map.of("request_id", "outer", "other", "preserved"));
        mvc.perform(get("/private/secret-path").queryParam("password", "secret-query")
                        .header("X-Request-ID", "client_123").header("Authorization", "Bearer secret-token"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID", "client_123"));
        var entries = events("http_request");
        assertEquals(1, entries.size());
        var event = entries.getFirst();
        assertEquals(Level.WARN, event.getLevel());
        assertEquals("client_123", event.getMDCPropertyMap().get("request_id"));
        assertEquals("<unmapped>", field(event, "endpoint"));
        assertEquals(401, field(event, "status"));
        assertTrue((double) field(event, "duration_ms") >= 0);
        assertFalse(event.getKeyValuePairs().toString().contains("secret"));
        assertEquals(Map.of("request_id", "outer", "other", "preserved"), MDC.getCopyOfContextMap());
    }

    @Test void authorizationFailureIsSummarizedWithoutStackTrace() throws Exception {
        mvc.perform(get("/api/v1/admin/businesses/suspended").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isForbidden());
        assertEquals(403, field(events("http_request").getFirst(), "status"));
        assertEquals(Level.WARN, events("http_request").getFirst().getLevel());
        assertTrue(capture.list.stream().noneMatch(e -> e.getThrowableProxy() != null));
    }

    @Test void missingInvalidLongAndRepeatedIdsAreReplaced() throws Exception {
        for (String[] ids : new String[][] {{}, {"bad id"}, {"a".repeat(65)}, {"one", "two"}, {"one,two"}}) {
            var request = get("/private");
            for (String id : ids) request.header("X-Request-ID", id);
            String returned = mvc.perform(request).andReturn().getResponse().getHeader("X-Request-ID");
            assertNotNull(UUID.fromString(returned));
            assertNull(MDC.get("request_id"));
        }
        assertEquals(5, events("http_request").size());
    }

    @Test void routeTemplateAndValidationExcludeSubmittedValues() throws Exception {
        mvc.perform(get("/api/v1/categories/probe/private-name").queryParam("q", "secret-query"))
                .andExpect(status().isOk());
        assertEquals("/api/v1/categories/probe/{value}", field(events("http_request").getFirst(), "endpoint"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"secret-invalid-email\",\"password\":\"secret-password\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(Level.INFO, events("http_request").getLast().getLevel());
        assertTrue(capture.list.stream().noneMatch(e -> e.getFormattedMessage().contains("secret-")));
    }

    @Test void handledFailureHasOneStackTraceAndOneSummary() throws Exception {
        when(authentication.login(any())).thenThrow(new IllegalStateException("diagnostic failure"));
        mvc.perform(post("/api/v1/auth/login").header("X-Request-ID", "failure-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isInternalServerError());
        var traces = capture.list.stream().filter(e -> e.getThrowableProxy() != null).toList();
        assertEquals(1, traces.size());
        assertEquals("failure-id", traces.getFirst().getMDCPropertyMap().get("request_id"));
        assertEquals(1, events("http_request").size());
        assertEquals(Level.ERROR, events("http_request").getFirst().getLevel());
        assertTrue(events("login_success").isEmpty());
    }

    @Test void successIsCorrelatedButFailedCommitIsNotReportedAsSuccess() throws Exception {
        when(authentication.login(any())).thenReturn(new TokenResponse("secret-jwt", "Bearer", 900,
                "secret-refresh", java.time.Instant.now()));
        mvc.perform(post("/api/v1/auth/login").header("X-Request-ID", "success-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk());
        assertEquals("success-id", events("login_success").getFirst().getMDCPropertyMap().get("request_id"));
        when(businesses.register(any(), any())).thenThrow(new org.springframework.transaction.TransactionSystemException("Commit failed"));
        mvc.perform(post("/api/v1/businesses").with(jwt().jwt(j -> j.subject("7")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uniqueName\":\"test_business\",\"information\":{\"name\":\"Test\",\"addressLine1\":\"123 Main Street\",\"cityId\":1}}"))
                .andExpect(status().isInternalServerError());
        assertTrue(events("business_registered").isEmpty());
        assertTrue(capture.list.stream().noneMatch(e -> e.getFormattedMessage().contains("secret-")));
    }

    @Test void refreshRejectionHasFixedReasonWithoutToken() throws Exception {
        when(authentication.refresh(any())).thenThrow(new InvalidRefreshTokenException());
        mvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + "x".repeat(43) + "\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals("invalid_refresh_token", field(events("refresh_rejected").getFirst(), "reason"));
    }

    @Test void successfulBusinessAndModerationEventsUseIdsWithoutRequestText() throws Exception {
        var business = new mithila001.service_advertiser.business.dto.BusinessResponse(12, "test_business", null,
                mithila001.service_advertiser.business.BusinessStatus.ACTIVE, null, null);
        when(businesses.register(any(), any())).thenReturn(business);
        when(moderation.suspend(any(), any(), any())).thenReturn(business);
        when(moderation.activate(any(), any(), any())).thenReturn(business);
        mvc.perform(post("/api/v1/businesses").with(jwt().jwt(j -> j.subject("7")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uniqueName\":\"test_business\",\"information\":{\"name\":\"Test\",\"addressLine1\":\"123 Main Street\",\"cityId\":1}}"))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/businesses/me").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isNoContent());
        for (String action : List.of("suspend", "activate")) {
            mvc.perform(post("/api/v1/admin/businesses/12/" + action)
                            .with(jwt().jwt(j -> j.subject("8")).authorities(
                                    new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"secret-moderation-text\"}"))
                    .andExpect(status().isOk());
        }
        for (String name : List.of("business_registered", "business_deleted", "business_suspended", "business_activated")) {
            assertEquals(1, events(name).size());
            assertEquals(Level.INFO, events(name).getFirst().getLevel());
        }
        assertEquals(12, field(events("business_suspended").getFirst(), "business_id"));
        assertTrue(capture.list.stream().noneMatch(e -> (e.getFormattedMessage() + e.getKeyValuePairs()).contains("secret-")));
    }

    @Test void healthSuccessIsQuietButFailureIsVisibleAndErrorDispatchIsSkipped() throws Exception {
        var filter = new RequestLoggingFilter();
        var request = new MockHttpServletRequest("GET", "/actuator/health");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertTrue(events("http_request").isEmpty());
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(503));
        assertEquals(503, field(events("http_request").getFirst(), "status"));
        request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
        request.setAttribute("jakarta.servlet.error.request_uri", "/actuator/health");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        assertEquals(1, events("http_request").size());
    }

    @Test void escapingFailureHasOneTraceAndClearsMdc() {
        var filter = new RequestLoggingFilter();
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(
                new MockHttpServletRequest("GET", "/private"), new MockHttpServletResponse(),
                (req, res) -> { throw new jakarta.servlet.ServletException("Infrastructure failed"); }));
        assertEquals(1, events("request_failure").size());
        assertEquals(500, field(events("http_request").getFirst(), "status"));
        assertNull(MDC.getCopyOfContextMap());
    }

    @RestController
    static class Probe {
        @GetMapping("/api/v1/categories/probe/{value}") String probe() { return "ok"; }
    }
}
