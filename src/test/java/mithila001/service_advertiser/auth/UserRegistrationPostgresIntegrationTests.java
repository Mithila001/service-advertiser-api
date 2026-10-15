package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.RegisterResponse;
import mithila001.service_advertiser.user.RoleRepository;
import mithila001.service_advertiser.user.UserRepository;
import mithila001.service_advertiser.user.UserRole;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
class UserRegistrationPostgresIntegrationTests {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
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

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    @Transactional
    void registrationPersistsUserRoleRelationshipAndBcryptHash() {
        String email = uniqueEmail();

        RegisterResponse response = registrationService.register(
                new RegisterRequest("Integration User", email, "SecurePass123!"));
        var user = userRepository.findByEmail(email).orElseThrow();

        assertEquals(response.id(), user.getId());
        assertEquals(UserRole.CUSTOMER, user.getRole().getName());
        assertEquals(user.getRole().getId(), roleRepository.findByName(UserRole.CUSTOMER).orElseThrow().getId());
        assertNotEquals("SecurePass123!", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
        assertTrue(passwordEncoder.matches("SecurePass123!", user.getPasswordHash()));
    }

    @Test
    void postgresEmailUniqueConstraintRejectsDuplicateRows() {
        String email = uniqueEmail();
        registrationService.register(new RegisterRequest("First User", email, "SecurePass123!"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO users (name, email, password_hash, status, role_id, created_at, updated_at)
                SELECT 'Duplicate User', ?, 'not-used', 'ACTIVE', id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM roles WHERE name = 'CUSTOMER'
                """, email));
    }

    private static String uniqueEmail() {
        return "integration-" + UUID.randomUUID() + "@example.com";
    }
}
