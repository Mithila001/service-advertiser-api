package mithila001.service_advertiser.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.auth.*;
import mithila001.service_advertiser.auth.dto.*;
import mithila001.service_advertiser.business.dto.*;

@SpringBootTest
@AutoConfigureMockMvc
class I_BusinessIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired AuthenticationService auth;
    @Autowired JwtDecoder decoder;
    @Autowired BusinessService businesses;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    BusinessRepository repository;

    private record Account(long id, TokenResponse tokens) {}

    private Account account() {
        String email = UUID.randomUUID() + "@example.com";
        long id = registration.register(new RegisterRequest("Owner", email, "SecurePass123!")).id();
        return new Account(id, auth.login(new LoginRequest(email, "SecurePass123!")));
    }

    private String uniqueName() { return "business_" + UUID.randomUUID().toString().replace("-", ""); }
    private Map<String, Object> information() { return Map.of("name", "Same display name", "city", "Colombo"); }
    private String json(Object value) { return mapper.writeValueAsString(value); }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Account owner) {
        return request.header("Authorization", "Bearer " + owner.tokens.accessToken());
    }
    private void register(Account owner, String name) throws Exception {
        mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("uniqueName", name, "information", information()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.uniqueName").value(name));
    }

    @Test
    void exposesSeededCatalogsAndAssignsBusinessCategories() throws Exception {
        mvc.perform(get("/api/v1/categories/business")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(25))
                .andExpect(jsonPath("$[0].name").value("Agriculture & Farming"))
                .andExpect(jsonPath("$[0].description").value("Farming agricultural supplies and agricultural services"));
        mvc.perform(get("/api/v1/categories/item")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(40))
                .andExpect(jsonPath("$[0].name").value("Accessories"));

        Account owner = account();
        Map<String, Object> info = Map.of("name", "Categorized", "city", "Colombo", "categoryIds", java.util.List.of(1, 3));
        mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("uniqueName", uniqueName(), "information", info))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.information.categoryIds[0]").value(1))
                .andExpect(jsonPath("$.information.categoryIds[1]").value(3));
        Integer businessId = jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id);
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM business_category_assignments WHERE business_id = ?",
                Integer.class, businessId));

        mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Changed", "city", "Galle", "categoryIds", java.util.List.of(2)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.information.categoryIds").isArray())
                .andExpect(jsonPath("$.information.categoryIds.length()").value(1))
                .andExpect(jsonPath("$.information.categoryIds[0]").value(2));
        mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Rejected", "city", "Galle", "categoryIds", java.util.List.of(1, 2, 3, 4)))))
                .andExpect(status().isBadRequest());
        mvc.perform(as(get("/api/v1/businesses/me"), owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.information.name").value("Changed"))
                .andExpect(jsonPath("$.information.categoryIds[0]").value(2));
        mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Changed", "city", "Galle"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.information.categoryIds").isEmpty());
    }

    @Test
    void rejectsInvalidBusinessCategorySelectionsAndCleansAssignmentsOnDelete() throws Exception {
        Account owner = account();
        String name = uniqueName();
        for (java.util.List<Integer> ids : java.util.List.of(java.util.List.of(1, 1), java.util.List.of(0),
                java.util.List.of(999), java.util.List.of(1, 2, 3, 4))) {
            Map<String, Object> info = Map.of("name", "Invalid", "city", "Colombo", "categoryIds", ids);
            mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", name, "information", info))))
                    .andExpect(status().isBadRequest());
        }
        Map<String, Object> info = Map.of("name", "Valid", "city", "Colombo", "categoryIds", java.util.List.of(1));
        mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("uniqueName", name, "information", info))))
                .andExpect(status().isCreated());
        Integer businessId = jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id);
        mvc.perform(as(delete("/api/v1/businesses/me"), owner)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business_category_assignments WHERE business_id = ?",
                Integer.class, businessId));
    }

    @Test
    void lifecyclePreservesSessionAndUsesCurrentRoleDespiteStaleJwt() throws Exception {
        Account owner = account();
        String name = uniqueName();
        register(owner, name);
        Integer id = jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id);
        assertNotNull(id);
        TokenResponse provider = auth.refresh(owner.tokens.refreshToken());
        assertEquals("PROVIDER", decoder.decode(provider.accessToken()).getClaimAsString("role"));
        mvc.perform(as(get("/api/v1/businesses/me"), owner)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/businesses/" + name)).andExpect(status().isOk());
        mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Updated", "city", "Galle", "uniqueName", "changed", "status", "DISABLED", "id", 999, "addressLine1", "123 Main Street", "addressLine2", "Floor 2"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.uniqueName").value(name))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.information.city").value("Galle"));
        mvc.perform(as(get("/api/v1/businesses/me"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.information.addressLine1").value("123 Main Street"))
                .andExpect(jsonPath("$.information.addressLine2").value("Floor 2"));
        mvc.perform(delete("/api/v1/businesses/me").header("Authorization", "Bearer " + provider.accessToken()))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business WHERE id = ?", Integer.class, id));
        assertNull(jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id));
        TokenResponse customer = auth.refresh(provider.refreshToken());
        assertEquals("CUSTOMER", decoder.decode(customer.accessToken()).getClaimAsString("role"));
        mvc.perform(get("/api/v1/businesses/" + name)).andExpect(status().isNotFound());
        // The deleted unique name can be claimed again; the old customer JWT remains usable.
        register(owner, name);
    }

    @Test
    void uniqueNamesConflictButDisplayNamesMayRepeatAndOwnerCannotRegisterTwice() throws Exception {
        Account first = account();
        Account second = account();
        String name = uniqueName();
        register(first, name);
        for (Account owner : new Account[] { first, second }) {
            mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", name, "information", information()))))
                    .andExpect(status().isConflict());
        }
        register(second, uniqueName());
    }

    @Test
    void validatesInputsAndRequiresAuthentication() throws Exception {
        Account owner = account();
        for (String name : new String[] { "ab", "Uppercase", "bad-name", "a".repeat(51) }) {
            mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", name, "information", information()))))
                    .andExpect(status().isBadRequest());
        }
        for (Map<String, Object> info : java.util.List.of(
                Map.<String, Object>of("name", "Test"), Map.<String, Object>of("name", " ", "city", "Colombo"),
                Map.<String, Object>of("name", "Test", "city", " "),
                Map.<String, Object>of("name", "Test", "city", "Colombo", "latitude", 91),
                Map.<String, Object>of("name", "Test", "city", "Colombo", "longitude", -181),
                Map.<String, Object>of("name", "Test", "city", "Colombo", "email", "invalid"))) {
            mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", uniqueName(), "information", info))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/businesses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/businesses/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/businesses/me")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/businesses/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void otherUsersAndInactiveOwnersCannotMutateBusiness() throws Exception {
        Account owner = account();
        Account other = account();
        String name = uniqueName();
        register(owner, name);
        mvc.perform(as(delete("/api/v1/businesses/me"), other)).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/v1/businesses/me"), other).contentType(MediaType.APPLICATION_JSON)
                .content(json(information()))).andExpect(status().isNotFound());
        for (String state : new String[] { "SUSPENDED", "DISABLED" }) {
            jdbc.update("UPDATE users SET status = ? WHERE id = ?", state, owner.id);
            mvc.perform(as(get("/api/v1/businesses/me"), owner)).andExpect(status().isForbidden());
            mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(information()))).andExpect(status().isForbidden());
            mvc.perform(as(delete("/api/v1/businesses/me"), owner)).andExpect(status().isForbidden());
            jdbc.update("UPDATE users SET status = ? WHERE id = ?", state, other.id);
            mvc.perform(as(post("/api/v1/businesses"), other).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", uniqueName(), "information", information()))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/businesses/" + name)).andExpect(status().isOk());
    }

    @Test
    void nonCustomerCannotRegisterEvenWithOldCustomerJwt() throws Exception {
        Account owner = account();
        for (String role : new String[] { "PROVIDER", "ADMIN" }) {
            jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = ?) WHERE id = ?", role, owner.id);
            mvc.perform(as(post("/api/v1/businesses"), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("uniqueName", uniqueName(), "information", information()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void failedRegistrationRollsBackInsertedBusiness() {
        Account owner = account();
        String name = uniqueName();
        BusinessInformation info = mapper.readValue(json(information()), BusinessInformation.class);
        org.mockito.Mockito.doAnswer(call -> {
            Business business = call.getArgument(0);
            entityManager.persist(business);
            entityManager.flush();
            throw new IllegalStateException("Test failure after business insert");
        }).when(repository).saveAndFlush(org.mockito.ArgumentMatchers.any(Business.class));
        assertThrows(IllegalStateException.class,
                () -> businesses.register(owner.id, new RegisterBusinessRequest(name, info)));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business WHERE unique_name = ?", Integer.class, name));
        assertNull(jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id));
        assertEquals("CUSTOMER", jdbc.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, owner.id));
    }

    @Test
    void failedDeletionRollsBackOwnershipAndRoleChanges() throws Exception {
        Account owner = account();
        String name = uniqueName();
        register(owner, name);
        org.mockito.Mockito.doThrow(new IllegalStateException("Test delete failure"))
                .when(repository).delete(org.mockito.ArgumentMatchers.any(Business.class));
        assertThrows(IllegalStateException.class, () -> businesses.delete(owner.id));
        assertNotNull(jdbc.queryForObject("SELECT business_id FROM users WHERE id = ?", Integer.class, owner.id));
        assertEquals("PROVIDER", jdbc.queryForObject(
                "SELECT r.name FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?", String.class, owner.id));
        mvc.perform(get("/api/v1/businesses/" + name)).andExpect(status().isOk());
    }

    @Test
    void concurrentRegistrationForOneOwnerCreatesExactlyOneBusiness() throws Exception {
        Account owner = account();
        BusinessInformation info = mapper.readValue(json(information()), BusinessInformation.class);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> action = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                businesses.register(owner.id, new RegisterBusinessRequest(uniqueName(), info));
                return true;
            } catch (org.springframework.web.server.ResponseStatusException ex) {
                assertEquals(409, ex.getStatusCode().value());
                return false;
            }
        };
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(action);
            var b = executor.submit(action);
            start.countDown();
            assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }
}
