package mithila001.service_advertiser.config;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler.ErrorResponse;
@Component
@org.springframework.context.annotation.Import(RateLimiter.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiProtectionFilter extends OncePerRequestFilter {
    private final RateLimiter limiter;
    private final ProtectionProperties properties;
    private final ObjectMapper mapper;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiProtectionFilter.class);
    public ApiProtectionFilter(RateLimiter limiter, ProtectionProperties properties, ObjectMapper mapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.mapper = mapper;
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        try {
            if ("POST".equals(request.getMethod())) {
                limiter.ip(path, request.getRemoteAddr());
            }
        } catch (RateLimiter.Rejected e) {
            response.setHeader("Retry-After", Long.toString(e.retry));
            boolean budgetExhausted = e.status == 429;
            log.atWarn()
                    .addKeyValue("event", budgetExhausted ? "rate_limit_rejected" : "rate_limit_capacity_rejected")
                    .addKeyValue("policy", e.policy)
                    .addKeyValue("reason", budgetExhausted ? "budget_exhausted" : "storage_full")
                    .log("Request admission rejected");
            error(response, e.status, budgetExhausted ? "Too Many Requests" : "Service Unavailable",
                    budgetExhausted ? "Too many requests. Please try again later." : "Service is temporarily unavailable");
            return;
        }
        boolean json = isJson(request.getContentType());
        if (path.startsWith("/api/v1/") && json) {
            int maximum = (int) properties.maxJsonBodySize().toBytes();
            byte[] body = request.getContentLengthLong() > maximum ? null : request.getInputStream().readNBytes(maximum + 1);
            if (body == null || body.length > maximum) {
                log.atWarn().addKeyValue("event", "request_size_rejected")
                        .addKeyValue("limit_bytes", maximum).addKeyValue("reason", "body_too_large")
                        .log("JSON body exceeds limit");
                error(response, 413, "Payload Too Large", "JSON request body is too large");
                return;
            }
            request = new HttpServletRequestWrapper(request) {
                public ServletInputStream getInputStream() {
                    var input = new ByteArrayInputStream(body);
                    return new ServletInputStream() {
                        public int read() { return input.read(); }
                        public int read(byte[] b, int off, int len) { return input.read(b, off, len); }
                        public boolean isFinished() { return input.available() == 0; }
                        public boolean isReady() { return true; }
                        public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous requests only"); }
                    };
                }
                public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
            };
        }
        chain.doFilter(request, response);
    }
    private static boolean isJson(String contentType) {
        if (contentType == null) return false;
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(type)
                    || (type.getType().equals("application") && type.getSubtype().endsWith("+json"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void error(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(status, error, message, Map.of()));
    }
}
