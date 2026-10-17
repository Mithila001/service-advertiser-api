package mithila001.service_advertiser.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.RegisterResponse;
import mithila001.service_advertiser.common.exception.DuplicateEmailException;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler;
import mithila001.service_advertiser.config.SecurityConfig;
import mithila001.service_advertiser.user.UserRole;

@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
class U_AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

    @Test
    void publicRegistrationReturnsCreatedResponseWithoutPassword() throws Exception {
        when(registrationService.register(any(RegisterRequest.class))).thenReturn(
                new RegisterResponse(7L, "Test User", "test@example.com", UserRole.CUSTOMER,
                        LocalDateTime.parse("2026-10-15T15:00:00")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test User","email":"test@example.com","password":"SecurePass123!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-15T15:00:00"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(registrationService).register(any(RegisterRequest.class));
    }

    @Test
    void invalidEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test User","email":"bad-email","password":"SecurePass123!"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").value("Must be a valid email address"));
    }

    @Test
    void missingFieldsReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is required"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Password is required"));
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(registrationService.register(any(RegisterRequest.class))).thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test User","email":"test@example.com","password":"SecurePass123!"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));
    }

    @Test
    void otherEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized());
    }
}
