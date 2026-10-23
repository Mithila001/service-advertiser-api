package mithila001.service_advertiser.auth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import mithila001.service_advertiser.config.SecurityConfig;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(value = AuthController.class, properties = {
    "app.protection.rate-limit.enabled=true", "app.protection.rate-limit.login-account-ip.capacity=1"})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class U_AuthProtectionTests {
    @Autowired MockMvc mvc;
    @MockitoBean RegistrationService registration;
    @MockitoBean AuthenticationService authentication;
    @MockitoBean JwtDecoder decoder;
    @Test void throttledLoginNeverCallsAuthenticationAndKeepsErrorEnvelope() throws Exception {
        when(authentication.login(any())).thenThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid email or password"));
        String body = "{\"email\":\"user@example.com\",\"password\":\"incorrect\"}";
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(body).header("X-Request-ID", "limit-check"))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
            .andExpect(header().string("X-Request-ID", "limit-check"))
            .andExpect(jsonPath("$.status").value(429)).andExpect(jsonPath("$.fieldErrors").isMap());
        verify(authentication, times(1)).login(any());
    }
}
