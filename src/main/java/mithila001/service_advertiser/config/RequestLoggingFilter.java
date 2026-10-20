package mithila001.service_advertiser.config;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var ids = Collections.list(request.getHeaders("X-Request-ID"));
        String id = ids.size() == 1 && SAFE_ID.matcher(ids.getFirst()).matches()
                ? ids.getFirst() : UUID.randomUUID().toString();
        var previous = MDC.getCopyOfContextMap();
        MDC.put("request_id", id);
        response.setHeader("X-Request-ID", id);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            log.atError().addKeyValue("event", "request_failure")
                    .setCause(exception).log("Unhandled request failure");
            throw exception;
        } finally {
            try {
                int status = failed ? 500 : response.getStatus();
                if (!("GET".equals(request.getMethod()) && "/actuator/health".equals(request.getRequestURI())
                        && status >= 200 && status < 300)) {
                    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                    Level level = status >= 500 ? Level.ERROR
                            : status == 401 || status == 403 ? Level.WARN : Level.INFO;
                    log.atLevel(level).addKeyValue("event", "http_request")
                            .addKeyValue("method", request.getMethod())
                            .addKeyValue("endpoint", route == null ? "<unmapped>" : route.toString())
                            .addKeyValue("status", status)
                            .addKeyValue("duration_ms", (System.nanoTime() - start) / 1_000_000.0)
                            .log("HTTP request completed");
                }
            } finally {
                if (previous == null) MDC.clear();
                else MDC.setContextMap(previous);
            }
        }
    }
}
