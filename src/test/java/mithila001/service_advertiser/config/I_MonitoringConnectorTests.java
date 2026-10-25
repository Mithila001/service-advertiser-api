package mithila001.service_advertiser.config;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import mithila001.service_advertiser.PostgresTestSupport;

@ActiveProfiles({"test", "monitoring"})
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0", "management.server.address=127.0.0.1"})
class I_MonitoringConnectorTests extends PostgresTestSupport {
    @Autowired Environment environment;

    @Test void metricsStayOnManagementSocketAndProbesRemainOnApplicationSocket() throws Exception {
        int application = environment.getRequiredProperty("local.server.port", Integer.class);
        int management = environment.getRequiredProperty("local.management.port", Integer.class);
        assertNotEquals(application, management);
        try (var client = HttpClient.newHttpClient()) {
            for (String path : new String[]{"/livez", "/readyz"}) {
                assertEquals(200, get(client, application, path).statusCode());
            }
            var metrics = get(client, management, "/actuator/prometheus");
            assertEquals(200, metrics.statusCode());
            assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
            assertTrue(metrics.body().contains("hikaricp_connections"));
            assertTrue(metrics.body().contains("http_server_requests_seconds_bucket"));
            assertNotEquals(200, get(client, application, "/actuator/prometheus").statusCode());
            for (int port : new int[]{application, management}) {
                assertNotEquals(200, get(client, port, "/actuator/env").statusCode());
                assertNotEquals(200, get(client, port, "/actuator/configprops").statusCode());
            }
            var health = get(client, management, "/actuator/health");
            assertEquals(200, health.statusCode());
            assertFalse(health.body().contains("components"));
            assertNotEquals(200, get(client, application, "/api/v1/businesses/me").statusCode());
        }
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
