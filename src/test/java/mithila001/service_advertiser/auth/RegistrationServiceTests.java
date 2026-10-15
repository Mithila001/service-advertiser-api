package mithila001.service_advertiser.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.RegisterResponse;
import mithila001.service_advertiser.common.exception.DuplicateEmailException;
import mithila001.service_advertiser.common.exception.RoleNotFoundException;
import mithila001.service_advertiser.user.Role;
import mithila001.service_advertiser.user.RoleRepository;
import mithila001.service_advertiser.user.User;
import mithila001.service_advertiser.user.UserRepository;
import mithila001.service_advertiser.user.UserRole;
import mithila001.service_advertiser.user.UserStatus;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTests {
    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new RegistrationService(userRepository, roleRepository, passwordEncoder);
    }

    @Test
    void registersCustomerWithNormalizedEmailAndHashedPassword() {
        Role customerRole = customerRole();
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName(UserRole.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("SecurePass123!")).thenReturn("$2b$test-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        RegisterResponse response = registrationService.register(
                new RegisterRequest("Test User", "  Test@Example.COM  ", "SecurePass123!"));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        User user = savedUser.getValue();
        assertEquals("test@example.com", user.getEmail());
        assertEquals("$2b$test-hash", user.getPasswordHash());
        assertEquals(UserRole.CUSTOMER, user.getRole().getName());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getBusinessId());
        assertTrue(user.getCreatedAt() != null);
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        verify(passwordEncoder).encode("SecurePass123!");

        assertEquals(42L, response.id());
        assertEquals(UserRole.CUSTOMER, response.role());
        assertFalse(Arrays.stream(RegisterResponse.class.getRecordComponents())
                .anyMatch(component -> component.getName().toLowerCase().contains("password")));
    }

    @Test
    void rejectsAnExistingEmailBeforeLookingUpRoleOrHashingPassword() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> registrationService.register(
                new RegisterRequest("Test User", "Test@Example.com", "SecurePass123!")));

        verify(roleRepository, never()).findByName(any());
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void failsWhenCustomerRoleIsMissing() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByName(UserRole.CUSTOMER)).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () -> registrationService.register(
                new RegisterRequest("Test User", "test@example.com", "SecurePass123!")));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    private Role customerRole() {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getName()).thenReturn(UserRole.CUSTOMER);
        return role;
    }
}
