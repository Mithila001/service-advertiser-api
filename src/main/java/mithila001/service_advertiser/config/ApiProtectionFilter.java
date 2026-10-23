package mithila001.service_advertiser.config;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
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
    private final RateLimiter limiter; private final ProtectionProperties properties; private final ObjectMapper mapper;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiProtectionFilter.class);
    public ApiProtectionFilter(RateLimiter limiter, ProtectionProperties properties, ObjectMapper mapper) {
        this.limiter=limiter; this.properties=properties; this.mapper=mapper;
    }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        try { if ("POST".equals(request.getMethod())) limiter.ip(path, request.getRemoteAddr()); }
        catch (RateLimiter.Rejected e) {
            response.setHeader("Retry-After", Long.toString(e.retry));
            log.atWarn().addKeyValue("event", e.status == 429 ? "rate_limit_rejected" : "rate_limit_capacity_rejected").addKeyValue("policy", e.policy).addKeyValue("reason", e.status == 429 ? "budget_exhausted" : "storage_full").log("Request admission rejected");
            error(response, e.status, e.status == 429 ? "Too Many Requests" : "Service Unavailable", e.status == 429 ? "Too many requests. Please try again later." : "Service is temporarily unavailable"); return;
        }
        boolean json = false;
        try { json = request.getContentType() != null && MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(request.getContentType()));
            if (request.getContentType() != null) { var type = MediaType.parseMediaType(request.getContentType()); json |= type.getType().equals("application") && type.getSubtype().endsWith("+json"); }
        } catch (IllegalArgumentException ignored) { }
        if (path.startsWith("/api/v1/") && json) {
            int maximum = (int) properties.maxJsonBodySize().toBytes();
            byte[] body = request.getContentLengthLong() > maximum ? null : request.getInputStream().readNBytes(maximum + 1);
            if (body == null || body.length > maximum) {
                log.atWarn().addKeyValue("event", "request_size_rejected").addKeyValue("limit_bytes", maximum).addKeyValue("reason", "body_too_large").log("JSON body exceeds limit");
                error(response, 413, "Payload Too Large", "JSON request body is too large"); return;
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
    private void error(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), new ErrorResponse(status, error, message, Map.of()));
    }
}
