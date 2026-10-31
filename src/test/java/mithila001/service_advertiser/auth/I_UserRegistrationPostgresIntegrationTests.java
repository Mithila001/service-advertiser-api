package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.RegisterResponse;
import mithila001.service_advertiser.user.RoleRepository;
import mithila001.service_advertiser.user.UserRepository;
import mithila001.service_advertiser.user.UserRole;
import mithila001.service_advertiser.PostgresTestSupport;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class I_UserRegistrationPostgresIntegrationTests extends PostgresTestSupport {

    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper mapper;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-31T10:15:30Z");

    @org.junit.jupiter.api.BeforeEach
    void fixedClock() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
        org.mockito.Mockito.when(clock.getZone()).thenReturn(java.time.ZoneId.of("Asia/Colombo"));
    }

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void registrationPersistsUserRoleRelationshipAndBcryptHash() {
        String email = uniqueEmail();

        RegisterResponse response = registrationService.register(
                new RegisterRequest("Integration User", email, "SecurePass123!", " +94 (77) 123-4567 "));
        var user = userRepository.findByEmail(email).orElseThrow();

        assertEquals(response.id(), user.getId());
        assertEquals("+94 (77) 123-4567", user.getPhone());
        assertEquals(UserRole.CUSTOMER, user.getRole().getName());
        assertEquals(user.getRole().getId(), roleRepository.findByName(UserRole.CUSTOMER).orElseThrow().getId());
        assertNotEquals("SecurePass123!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("SecurePass123!", user.getPasswordHash()));
    }

    @Test
    void postgresEmailUniqueConstraintRejectsDuplicateRows() {
        String email = uniqueEmail();
        registrationService.register(new RegisterRequest("First User", email, "SecurePass123!", "+94771234567"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO users (name, email, password_hash, phone, status, role_id, created_at, updated_at)
                SELECT 'Duplicate User', ?, 'not-used', '+94771234567', 'ACTIVE', id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM roles WHERE name = 'CUSTOMER'
                """, email));
    }

    @Test
    void postgresRequiresPhoneAndStreetAddress() {
        var response = registrationService.register(new RegisterRequest("Constraints", uniqueEmail(), "SecurePass123!", "+94771234567"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update("UPDATE users SET phone=NULL WHERE id=?", response.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO business (name, unique_name, created_at, updated_at)
                VALUES ('Missing address', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "constraint_" + UUID.randomUUID().toString().replace("-", "")));
    }

    @Test
    @Transactional
    void registrationPersistsAndSerializesTheSameInstantInANonUtcSession() throws Exception {
        jdbcTemplate.execute("SET LOCAL TIME ZONE 'Asia/Colombo'");
        var request = new RegisterRequest("Timezone", uniqueEmail(), "SecurePass123!", "+94771234567");
        var body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/register")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var response = mapper.readTree(body);
        assertEquals(NOW, java.time.Instant.parse(response.get("createdAt").asString()));
        assertEquals(NOW, jdbcTemplate.queryForObject("SELECT created_at FROM users WHERE id=?",
                java.time.OffsetDateTime.class, response.get("id").asLong()).toInstant());
        assertTrue(!response.has("phone"));
    }

    private static String uniqueEmail() {
        return "integration-" + UUID.randomUUID() + "@example.com";
    }
}
