package mithila001.service_advertiser.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.auth.*;
import mithila001.service_advertiser.auth.dto.*;
import mithila001.service_advertiser.business.dto.*;
import mithila001.service_advertiser.item.*;
import mithila001.service_advertiser.item.dto.CreateItemRequest;

@SpringBootTest
@AutoConfigureMockMvc
class I_BusinessModerationIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired AuthenticationService auth;
    @Autowired BusinessService businesses;
    @Autowired BusinessModerationService moderation;
    @Autowired ItemService items;
    @Autowired PlatformTransactionManager transactions;

    private record Account(long id, String token) {}
    private Account account(boolean admin) {
        String email = UUID.randomUUID() + "@example.com";
        long id = registration.register(new RegisterRequest("Moderator test", email, "SecurePass123!")).id();
        if (admin) jdbc.update("UPDATE users SET role_id=(SELECT id FROM roles WHERE name='ADMIN') WHERE id=?", id);
        return new Account(id, auth.login(new LoginRequest(email, "SecurePass123!")).accessToken());
    }
    private BusinessInformationRequest information(String name) {
        return new BusinessInformationRequest(name, null, null, null, null, null, null,
                1, null, null, null, null, List.of());
    }
    private BusinessResponse business(Account owner) {
        String name = "moderation_" + UUID.randomUUID().toString().replace("-", "");
        return businesses.register(owner.id(), new RegisterBusinessRequest(name, information(name)));
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Account account) {
        return request.header("Authorization", "Bearer " + account.token());
    }

    @Test
    void suspensionHidesPublicDataBlocksOwnerWritesAndReactivationPreservesItemsAndHistory() throws Exception {
        var admin = account(true);
        var owner = account(false);
        var business = business(owner);
        var item = items.create(owner.id(), new CreateItemRequest(business.uniqueName(), null, ItemType.PRODUCT, null, List.of()));
        var inactive = items.create(owner.id(), new CreateItemRequest("Inactive", null, ItemType.SERVICE, null, List.of()));
        jdbc.update("UPDATE items SET status='INACTIVE' WHERE id=?", inactive.id());
        String path = "/api/v1/admin/businesses/" + business.id();

        mvc.perform(as(post(path + "/suspend"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" Report reviewed \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(as(post(path + "/suspend"), admin)).andExpect(status().isConflict());
        mvc.perform(as(get("/api/v1/admin/businesses/suspended"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", hasItem(business.id())));
        mvc.perform(get("/api/v1/businesses/" + business.uniqueName())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isNotFound());
        for (String url : List.of("/api/v1/businesses?q=" + business.uniqueName(),
                "/api/v1/items?q=" + business.uniqueName(), "/api/v1/businesses/" + business.uniqueName() + "/items")) {
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(as(get("/api/v1/businesses/me"), owner)).andExpect(status().isOk());
        mvc.perform(as(put("/api/v1/businesses/me"), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Changed\",\"cityId\":1}" )).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/businesses/me"), owner)).andExpect(status().isForbidden());
        mvc.perform(as(post("/api/v1/businesses/me/items"), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"New\",\"type\":\"PRODUCT\"}" )).andExpect(status().isForbidden());
        mvc.perform(as(patch("/api/v1/items/" + item.id()), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Changed\"}" )).andExpect(status().isForbidden());
        mvc.perform(as(delete("/api/v1/items/" + item.id()), owner)).andExpect(status().isForbidden());

        mvc.perform(as(post(path + "/activate"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(as(post(path + "/activate"), admin)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/businesses/" + business.uniqueName())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/items/" + inactive.id())).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/businesses/" + business.uniqueName() + "/items"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(as(get(path + "/history"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("ACTIVATED"))
                .andExpect(jsonPath("$[1].action").value("SUSPENDED"))
                .andExpect(jsonPath("$[1].adminId").value(admin.id()))
                .andExpect(jsonPath("$[1].businessId").value(business.id()))
                .andExpect(jsonPath("$[1].reason").value("Report reviewed"));
    }

    @Test
    void requiresCurrentActiveAdminAndValidRequestsWithoutCreatingSpuriousHistory() throws Exception {
        var admin = account(true);
        var owner = account(false);
        var business = business(owner);
        String path = "/api/v1/admin/businesses/" + business.id();
        for (String suffix : List.of("/suspend", "/activate")) {
            mvc.perform(post(path + suffix)).andExpect(status().isUnauthorized());
            mvc.perform(as(post(path + suffix), owner)).andExpect(status().isForbidden());
        }
        mvc.perform(as(get(path + "/history"), owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/businesses/suspended")).andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/v1/admin/businesses/suspended"), owner)).andExpect(status().isForbidden());
        mvc.perform(as(post(path + "/suspend"), admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + "x".repeat(501) + "\"}" )).andExpect(status().isBadRequest());
        mvc.perform(as(post("/api/v1/admin/businesses/2147483647/suspend"), admin)).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/admin/businesses/suspended?page=-1"), admin)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?", admin.id());
        mvc.perform(as(post(path + "/suspend"), admin)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/v1/admin/businesses/suspended"), admin)).andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET status='ACTIVE', role_id=(SELECT id FROM roles WHERE name='CUSTOMER') WHERE id=?", admin.id());
        mvc.perform(as(post(path + "/suspend"), admin)).andExpect(status().isForbidden());
        mvc.perform(as(get(path + "/history"), admin)).andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM business_moderation_history WHERE business_id=?", Integer.class, business.id()));
    }

    @Test
    void ownerUpdateWaitsForSuspensionAndCannotOverwriteIt() throws Exception {
        var admin = account(true);
        var owner = account(false);
        var business = business(owner);
        var started = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> update = new TransactionTemplate(transactions).execute(transaction -> {
                moderation.suspend(admin.id(), business.id(), null);
                Future<?> pending = executor.submit(() -> {
                    started.countDown();
                    assertThrows(AccessDeniedException.class, () -> businesses.update(owner.id(), information("Changed")));
                });
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> pending.get(200, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    throw new AssertionError(exception);
                }
                return pending;
            });
            update.get(10, TimeUnit.SECONDS);
        }
        assertEquals("SUSPENDED", jdbc.queryForObject("SELECT status FROM business WHERE id=?", String.class, business.id()));
        assertEquals(business.uniqueName(), jdbc.queryForObject("SELECT name FROM business WHERE id=?", String.class, business.id()));
    }
}
