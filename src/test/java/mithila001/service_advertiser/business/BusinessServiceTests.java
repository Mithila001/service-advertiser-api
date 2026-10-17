package mithila001.service_advertiser.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.business.dto.*;
import mithila001.service_advertiser.user.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTests {
    @Mock BusinessRepository businesses;
    @Mock UserRepository users;
    @Mock RoleRepository roles;
    BusinessService service;
    User user;
    final Clock clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC);
    final BusinessInformation info = new BusinessInformation("Display", null, null, null, null,
            null, null, "Colombo", null, null, null, null, null, null);

    @BeforeEach
    void setup() {
        service = new BusinessService(businesses, users, roles, clock);
        user = new User();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role(UserRole.CUSTOMER));
        when(users.findForUpdate(1L)).thenReturn(Optional.of(user));
    }

    private Role role(UserRole value) {
        Role role = mock(Role.class);
        lenient().when(role.getName()).thenReturn(value);
        return role;
    }

    @Test
    void registersActiveBusinessAndPromotesOwner() {
        Role provider = role(UserRole.PROVIDER);
        when(roles.findByName(UserRole.PROVIDER)).thenReturn(Optional.of(provider));
        when(businesses.saveAndFlush(any())).thenAnswer(call -> {
            Business business = call.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(business, "id", 7);
            return business;
        });
        BusinessResponse result = service.register(1L, new RegisterBusinessRequest("test_business", info));
        assertEquals(7, user.getBusinessId());
        assertSame(provider, user.getRole());
        assertEquals(BusinessStatus.ACTIVE, result.status());
        assertEquals("test_business", result.uniqueName());
        assertEquals(LocalDateTime.now(clock), result.createdAt());
    }

    @Test
    void rejectsInactiveNonCustomerAndAlreadyOwningUsersBeforeCreatingBusiness() {
        user.setStatus(UserStatus.SUSPENDED);
        var request = new RegisterBusinessRequest("test_business", info);
        assertThrows(AccessDeniedException.class, () -> service.register(1L, request));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(role(UserRole.ADMIN));
        assertThrows(AccessDeniedException.class, () -> service.register(1L, request));
        user.setBusinessId(7);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(1L, request)).getStatusCode().value());
        verifyNoInteractions(businesses);
    }

    @Test
    void rejectsDuplicateUniqueName() {
        when(businesses.existsByUniqueName("taken")).thenReturn(true);
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.register(1L, new RegisterBusinessRequest("taken", info))).getStatusCode().value());
        verify(businesses, never()).saveAndFlush(any());
    }

    @Test
    void updatesOnlyOwnedBusinessAndDeletesAfterClearingForeignKey() {
        user.setBusinessId(7);
        Business business = new Business("immutable_name", info, LocalDateTime.now(clock).minusDays(1));
        when(businesses.findById(7)).thenReturn(Optional.of(business));
        BusinessResponse result = service.update(1L, info);
        assertEquals("immutable_name", result.uniqueName());
        assertEquals(LocalDateTime.now(clock), result.updatedAt());
        Role customer = role(UserRole.CUSTOMER);
        when(roles.findByName(UserRole.CUSTOMER)).thenReturn(Optional.of(customer));
        doAnswer(call -> {
            assertNull(user.getBusinessId());
            assertSame(customer, user.getRole());
            return null;
        }).when(users).flush();
        service.delete(1L);
        var order = inOrder(users, businesses);
        order.verify(users).flush();
        order.verify(businesses).delete(business);
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.delete(1L)).getStatusCode().value());
    }
}
