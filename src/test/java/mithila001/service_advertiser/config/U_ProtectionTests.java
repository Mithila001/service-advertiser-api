package mithila001.service_advertiser.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.springframework.util.unit.DataSize;
class U_ProtectionTests {
    private RateLimiter limiter(boolean enabled) {
        var policy = new ProtectionProperties.Policy(1, Duration.ofMinutes(1));
        return new RateLimiter(new ProtectionProperties(DataSize.ofKilobytes(64),
            new ProtectionProperties.RateLimit(enabled, policy, policy, policy, policy)));
    }
    @Test void independentKeysAndNormalizedAccounts() {
        var limiter = limiter(true);
        limiter.login(" USER@example.com ", "one");
        var rejected = assertThrows(RateLimiter.Rejected.class, () -> limiter.login("user@example.com", "one"));
        assertEquals(429, rejected.status); assertTrue(rejected.retry > 0);
        limiter.login("user@example.com", "two");
        limiter.login("other@example.com", "one");
        limiter.ip("/api/v1/auth/login", "one");
        limiter.ip("/api/v1/auth/refresh", "one");
    }
    @Test void disabledAndInvalidConfiguration() {
        var limiter = limiter(false);
        for (int i=0;i<10;i++) limiter.login("user@example.com", "one");
        assertThrows(IllegalArgumentException.class, () -> new ProtectionProperties.Policy(0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new ProtectionProperties(DataSize.ofBytes(0), null));
    }
    @Test void boundedBodyAndCorrelation() throws Exception {
        var policy = new ProtectionProperties.Policy(2, Duration.ofMinutes(1));
        var props = new ProtectionProperties(DataSize.ofBytes(16), new ProtectionProperties.RateLimit(true, policy, policy, policy, policy));
        var filter = new ApiProtectionFilter(new RateLimiter(props), props, tools.jackson.databind.json.JsonMapper.builder().build());
        for (int size : new int[]{15,16,17}) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/items");
            request.setContentType("application/problem+json"); request.setContent(" ".repeat(size).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            filter.doFilter(request, response, (req, res) -> {
                assertEquals(size, req.getInputStream().readAllBytes().length); calls.incrementAndGet();
            });
            assertEquals(size > 16 ? 413 : 200, response.getStatus());
            assertEquals(size > 16 ? 0 : 1, calls.get());
        }
    }
    @Test void chunkedAndUnreliableLengthStillBounded() throws Exception {
        var policy = new ProtectionProperties.Policy(2, Duration.ofMinutes(1));
        var props = new ProtectionProperties(DataSize.ofBytes(16), new ProtectionProperties.RateLimit(false, policy, policy, policy, policy));
        var filter = new ApiProtectionFilter(new RateLimiter(props), props, tools.jackson.databind.json.JsonMapper.builder().build());
        for (long declared : new long[]{-1,1}) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/items") {
                public long getContentLengthLong() { return declared; }
            };
            request.setContentType("application/json"); request.setContent(" ".repeat(17).getBytes());
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            filter.doFilter(request, response, (req,res) -> fail("Oversized request reached service"));
            assertEquals(413, response.getStatus());
        }
    }
    @Test void forwardedHeadersDoNotChangeBudget() throws Exception {
        var policy = new ProtectionProperties.Policy(1, Duration.ofMinutes(1));
        var props = new ProtectionProperties(DataSize.ofBytes(16), new ProtectionProperties.RateLimit(true, policy, policy, policy, policy));
        var filter = new ApiProtectionFilter(new RateLimiter(props), props, tools.jackson.databind.json.JsonMapper.builder().build());
        for (int attempt=0; attempt<2; attempt++) {
            var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("127.0.0.1"); request.addHeader("X-Forwarded-For", "192.0.2." + attempt);
            var response = new org.springframework.mock.web.MockHttpServletResponse();
            filter.doFilter(request, response, (req,res) -> {});
            assertEquals(attempt == 0 ? 200 : 429, response.getStatus());
            if (attempt == 1) assertNotNull(response.getHeader("Retry-After"));
        }
    }
    @Test void refillAndConcurrentConsumption() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong();
        var meter = new io.github.bucket4j.TimeMeter() {
            public long currentTimeNanos() { return now.get(); }
            public boolean isWallClockBased() { return false; }
        };
        var policy = new ProtectionProperties.Policy(1, Duration.ofSeconds(1));
        var props = new ProtectionProperties(DataSize.ofBytes(16), new ProtectionProperties.RateLimit(true, policy, policy, policy, policy));
        var limiter = new RateLimiter(props, meter);
        limiter.login("user@example.com", "one");
        assertThrows(RateLimiter.Rejected.class, () -> limiter.login("user@example.com", "one"));
        now.set(1000000000L);
        var admitted = new java.util.concurrent.atomic.AtomicInteger();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i=0;i<20;i++) tasks.add(pool.submit(() -> {
                try { limiter.login("user@example.com", "one"); admitted.incrementAndGet(); }
                catch (RateLimiter.Rejected expected) { }
            }));
            for (var task : tasks) task.get();
        }
        assertEquals(1, admitted.get());
        now.set(Duration.ofMinutes(16).toNanos()); limiter.cleanup();
        limiter.login("user@example.com", "one");
    }
    @Test void storageCapacityDoesNotResetExistingBuckets() {
        var limiter = limiter(true);
        for (int i=0;i<10000;i++) limiter.ip("/api/v1/auth/login", "ip-" + i);
        assertEquals(503, assertThrows(RateLimiter.Rejected.class, () -> limiter.ip("/api/v1/auth/login", "new")).status);
        assertEquals(429, assertThrows(RateLimiter.Rejected.class, () -> limiter.ip("/api/v1/auth/login", "ip-0")).status);
    }
    @Test void validationReportsPasswordAndDescriptionFields() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var registration = new mithila001.service_advertiser.auth.dto.RegisterRequest("User", "user@example.com", "a".repeat(73));
            assertTrue(validator.validate(registration).stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
            var item = new mithila001.service_advertiser.item.dto.CreateItemRequest("Title", "x".repeat(10001), mithila001.service_advertiser.item.ItemType.SERVICE, null);
            assertTrue(validator.validate(item).stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
        }
    }
    @Test void mainConfigurationEnablesRateLimits() throws Exception {
        var source = new org.springframework.boot.env.YamlPropertySourceLoader().load("main", new org.springframework.core.io.FileSystemResource("src/main/resources/application.yaml")).getFirst();
        assertEquals("${RATE_LIMIT_ENABLED:true}", source.getProperty("app.protection.rate-limit.enabled"));
        assertEquals("${RATE_LIMIT_LOGIN_IP_CAPACITY:120}", source.getProperty("app.protection.rate-limit.login-ip.capacity"));
    }
    @Test void passwordUsesUtf8Bytes() {
        var validator = new mithila001.service_advertiser.auth.BcryptPasswordLength.Validator();
        assertTrue(validator.isValid("a".repeat(72), null));
        assertFalse(validator.isValid("a".repeat(73), null));
        assertTrue(validator.isValid("\u00e9".repeat(36), null));
        assertFalse(validator.isValid("\u00e9".repeat(37), null));
    }
}
