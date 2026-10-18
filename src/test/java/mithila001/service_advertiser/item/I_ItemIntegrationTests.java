package mithila001.service_advertiser.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.auth.*;
import mithila001.service_advertiser.auth.dto.*;
import mithila001.service_advertiser.business.*;
import mithila001.service_advertiser.business.dto.*;
import mithila001.service_advertiser.item.dto.*;

@SpringBootTest
@AutoConfigureMockMvc
class I_ItemIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired AuthenticationService auth;
    @Autowired BusinessService businesses;
    @Autowired ItemService items;

    private record Owner(long userId, String token, String uniqueName, int businessId) {}
    private Owner owner(boolean withBusiness) {
        String email = UUID.randomUUID() + "@example.com";
        long id = registration.register(new RegisterRequest("Owner", email, "SecurePass123!")).id();
        String token = auth.login(new LoginRequest(email, "SecurePass123!")).accessToken();
        String name = "items_" + UUID.randomUUID().toString().replace("-", "");
        int businessId = 0;
        if (withBusiness) {
            var info = new BusinessInformation("Business", null, null, null, null, null, null, "Colombo",
                    null, null, null, null, null, null);
            businessId = businesses.register(id, new RegisterBusinessRequest(name, info)).id();
        }
        // Deliberately retain the CUSTOMER JWT to prove authorization uses database state.
        return new Owner(id, token, name, businessId);
    }
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, Owner owner) {
        return request.header("Authorization", "Bearer " + owner.token);
    }
    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Object body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }
    private ItemResponse create(Owner owner, String type, Object price) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Test " + type);
        body.put("type", type);
        if (price != null) body.put("price", price);
        String response = mvc.perform(json(as(post("/api/v1/businesses/me/items"), owner), body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.businessId").value(owner.businessId))
                .andReturn().getResponse().getContentAsString();
        return mapper.readValue(response, ItemResponse.class);
    }

    @Test
    void createsBothTypesAndSupportsPartialUpdatesNullClearingAndPermanentDeletion() throws Exception {
        Owner owner = owner(true);
        ItemResponse product = create(owner, "PRODUCT", "12.34");
        ItemResponse service = create(owner, "SERVICE", null);
        assertEquals(new BigDecimal("12.34"), product.price());
        assertNull(service.price());
        assertEquals(List.of(), service.categoryIds());
        mvc.perform(get("/api/v1/items/" + product.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(12.34));
        mvc.perform(json(as(patch("/api/v1/items/" + product.id()), owner),
                Map.of("title", "Updated", "description", "Details", "type", "SERVICE", "status", "INACTIVE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(12.34))
                .andExpect(jsonPath("$.type").value("SERVICE"));
        mvc.perform(get("/api/v1/items/" + product.id())).andExpect(status().isNotFound());
        mvc.perform(as(get("/api/v1/businesses/me/items"), owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(as(patch("/api/v1/items/" + product.id()), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":null,\"description\":null,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.price").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.title").value("Updated"));
        mvc.perform(get("/api/v1/items/" + product.id())).andExpect(status().isOk());
        mvc.perform(as(delete("/api/v1/items/" + product.id()), owner)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/items/" + product.id())).andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM items WHERE id = ?", Integer.class, product.id()));
    }

    @Test
    void assignsItemCategoriesWithPatchSemanticsAndCleansAssignmentsOnDelete() throws Exception {
        Owner owner = owner(true);
        Map<String, Object> body = Map.of("title", "Categorized", "type", "PRODUCT", "categoryIds", List.of(2, 1));
        String response = mvc.perform(json(as(post("/api/v1/businesses/me/items"), owner), body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoryIds[0]").value(1))
                .andExpect(jsonPath("$.categoryIds[1]").value(2)).andReturn().getResponse().getContentAsString();
        ItemResponse item = mapper.readValue(response, ItemResponse.class);
        mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[0]").value(1));

        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("title", "Still categorized")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryIds.length()").value(2));
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("categoryIds", List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryIds").isEmpty());
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("categoryIds", List.of(2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryIds[0]").value(2));
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("categoryIds", List.of(1, 2, 3))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[0]").value(2));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM item_category_assignments WHERE item_id = ?",
                Integer.class, item.id()));
        mvc.perform(as(delete("/api/v1/items/" + item.id()), owner)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM item_category_assignments WHERE item_id = ?",
                Integer.class, item.id()));
    }

    @Test
    void rejectsInvalidItemCategorySelectionsAndProtectsOwnership() throws Exception {
        Owner owner = owner(true);
        Owner other = owner(true);
        for (String categories : new String[] { "[1,1]", "[0]", "[999]", "[1,2,3]", "[1,null]" }) {
            String body = "{\"title\":\"Bad\",\"type\":\"PRODUCT\",\"categoryIds\":" + categories + "}";
            mvc.perform(as(post("/api/v1/businesses/me/items"), owner).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(as(post("/api/v1/businesses/me/items"), owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"No categories\",\"type\":\"PRODUCT\",\"categoryIds\":null}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categoryIds").isEmpty());
        ItemResponse item = create(owner, "PRODUCT", null);
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("categoryIds", List.of(1))))
                .andExpect(status().isOk());
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), other), Map.of("categoryIds", List.of(2))))
                .andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM item_category_assignments WHERE item_id = ?",
                Integer.class, item.id()));
    }

    @Test
    void validatesFieldsAndRejectsNullForRequiredPatchFields() throws Exception {
        Owner owner = owner(true);
        for (Map<String, Object> body : List.of(
                Map.<String, Object>of("type", "PRODUCT"), Map.<String, Object>of("title", "Item"),
                Map.<String, Object>of("title", " ", "type", "PRODUCT"),
                Map.<String, Object>of("title", "a".repeat(151), "type", "PRODUCT"),
                Map.<String, Object>of("title", "Item", "type", "OTHER"),
                Map.<String, Object>of("title", "Item", "type", "PRODUCT", "price", -1),
                Map.<String, Object>of("title", "Item", "type", "PRODUCT", "price", "1.001"),
                Map.<String, Object>of("title", "Item", "type", "PRODUCT", "price", "100000000000000000"))) {
            mvc.perform(json(as(post("/api/v1/businesses/me/items"), owner), body)).andExpect(status().isBadRequest());
        }
        ItemResponse item = create(owner, "PRODUCT", 0);
        assertEquals(0, item.price().signum());
        for (String patch : new String[] { "{\"title\":null}", "{\"type\":null}", "{\"status\":null}",
                "{\"title\":\" \"}", "{\"price\":-1}", "{\"price\":1.001}", "{\"status\":\"INVALID\"}" }) {
            mvc.perform(as(patch("/api/v1/items/" + item.id()), owner).contentType(MediaType.APPLICATION_JSON)
                    .content(patch)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/items/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items?page=oops"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesOwnershipDatabaseRoleAndAccountState() throws Exception {
        Owner owner = owner(true);
        Owner other = owner(true);
        Owner customer = owner(false);
        ItemResponse item = create(owner, "PRODUCT", null);
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), other), Map.of("title", "Hijacked")))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/v1/items/" + item.id()), other)).andExpect(status().isNotFound());
        mvc.perform(json(as(post("/api/v1/businesses/me/items"), customer), Map.of("title", "Item", "type", "PRODUCT")))
                .andExpect(status().isForbidden());
        // Client ownership fields have no effect.
        mvc.perform(json(as(post("/api/v1/businesses/me/items"), other),
                Map.of("title", "Mine", "type", "SERVICE", "businessId", owner.businessId, "status", "INACTIVE")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.businessId").value(other.businessId))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'CUSTOMER') WHERE id = ?", owner.userId);
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("title", "No")))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role_id = (SELECT id FROM roles WHERE name = 'PROVIDER'), status = 'SUSPENDED' WHERE id = ?", owner.userId);
        mvc.perform(as(delete("/api/v1/items/" + item.id()), owner)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/businesses/me/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/businesses/me/items")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/items/" + item.id())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/items/" + item.id())).andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedAndDisabledBusinessesCannotMutateItemsAndAreHiddenPublicly() throws Exception {
        Owner owner = owner(true);
        ItemResponse item = create(owner, "SERVICE", null);
        for (String state : new String[] { "SUSPENDED", "DISABLED" }) {
            jdbc.update("UPDATE business SET status = ? WHERE id = ?", state, owner.businessId);
            mvc.perform(json(as(post("/api/v1/businesses/me/items"), owner), Map.of("title", "Item", "type", "PRODUCT")))
                    .andExpect(status().isForbidden());
            mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("title", "Changed")))
                    .andExpect(status().isForbidden());
            mvc.perform(as(delete("/api/v1/items/" + item.id()), owner)).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(as(get("/api/v1/businesses/me/items"), owner)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Test
    void paginatesOnlyActiveItemsFromTheRequestedBusiness() throws Exception {
        Owner owner = owner(true);
        Owner other = owner(true);
        ItemResponse first = create(owner, "PRODUCT", null);
        ItemResponse inactive = create(owner, "SERVICE", null);
        ItemResponse last = create(owner, "SERVICE", null);
        create(other, "PRODUCT", null);
        mvc.perform(json(as(patch("/api/v1/items/" + inactive.id()), owner), Map.of("status", "INACTIVE")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items?size=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first.id()))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items?size=1&page=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(last.id()));
        mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items?page=5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        for (String query : new String[] { "page=-1", "size=0", "size=101" }) {
            mvc.perform(get("/api/v1/businesses/" + owner.uniqueName + "/items?" + query)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void allItemsCountTowardLimitAndDeletingFreesSlot() throws Exception {
        Owner owner = owner(true);
        ItemResponse first = create(owner, "PRODUCT", null);
        for (int i = 1; i < 10; i++) create(owner, "SERVICE", null);
        mvc.perform(json(as(patch("/api/v1/items/" + first.id()), owner), Map.of("status", "INACTIVE")))
                .andExpect(status().isOk());
        mvc.perform(json(as(post("/api/v1/businesses/me/items"), owner), Map.of("title", "Overflow", "type", "PRODUCT")))
                .andExpect(status().isConflict());
        mvc.perform(as(delete("/api/v1/items/" + first.id()), owner)).andExpect(status().isNoContent());
        create(owner, "PRODUCT", null);
        assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM items WHERE business_id = ?", Integer.class, owner.businessId));
    }

    @Test
    void concurrentCreationCannotExceedLimit() throws Exception {
        Owner owner = owner(true);
        for (int i = 0; i < 9; i++) create(owner, "PRODUCT", null);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> action = () -> {
            assertTrue(start.await(10, TimeUnit.SECONDS));
            try {
                items.create(owner.userId, new CreateItemRequest("Concurrent", null, ItemType.SERVICE, null));
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
        assertEquals(10, jdbc.queryForObject("SELECT count(*) FROM items WHERE business_id = ?", Integer.class, owner.businessId));
    }

    @Test
    void deletingBusinessCascadesAllItemsIncludingInactive() throws Exception {
        Owner owner = owner(true);
        ItemResponse item = create(owner, "PRODUCT", null);
        create(owner, "SERVICE", null);
        mvc.perform(json(as(patch("/api/v1/items/" + item.id()), owner), Map.of("status", "INACTIVE")))
                .andExpect(status().isOk());
        mvc.perform(as(delete("/api/v1/businesses/me"), owner)).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM items WHERE business_id = ?", Integer.class, owner.businessId));
        mvc.perform(get("/api/v1/items/" + item.id())).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, owner.userId));
    }
}
