package standalone.shutdownfixture;
import java.util.concurrent.*;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import mithila001.service_advertiser.config.*;
import mithila001.service_advertiser.config.RequestLoggingFilter;
@SpringBootConfiguration(proxyBeanMethods=false)
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,FlywayAutoConfiguration.class,HibernateJpaAutoConfiguration.class,SecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class})
@EnableConfigurationProperties(ProtectionProperties.class)
@Import({ShutdownTestApplication.Probe.class,ApiProtectionFilter.class,RateLimiter.class,RequestLoggingFilter.class})
public class ShutdownTestApplication {
    @Bean static SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
    }
    @RestController public static class Probe {
        public final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        @GetMapping("/api/v1/categories/shutdown-probe") public String waitForRelease() throws InterruptedException { entered.countDown();release.await(8,TimeUnit.SECONDS);return "finished"; }
        @PostMapping("/api/v1/probe") public int body(@RequestBody byte[] body) { return body.length; }
    }
    public static ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext)new org.springframework.boot.builder.SpringApplicationBuilder(ShutdownTestApplication.class).properties(
            "server.port=0","server.shutdown=graceful","spring.lifecycle.timeout-per-shutdown-phase=2s",
            "app.protection.max-json-body-size=64KB","app.protection.rate-limit.enabled=false").run();
    }
}
