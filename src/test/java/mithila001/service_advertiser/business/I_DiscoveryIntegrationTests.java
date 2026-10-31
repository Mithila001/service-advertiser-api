package mithila001.service_advertiser.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import mithila001.service_advertiser.PostgresTestSupport;
import mithila001.service_advertiser.auth.RegistrationService;
import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.business.dto.BusinessInformationRequest;
import mithila001.service_advertiser.business.dto.RegisterBusinessRequest;
import mithila001.service_advertiser.item.ItemService;
import mithila001.service_advertiser.item.ItemType;
import mithila001.service_advertiser.item.dto.CreateItemRequest;

@SpringBootTest
@AutoConfigureMockMvc
class I_DiscoveryIntegrationTests extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registration;
    @Autowired BusinessService businesses;
    @Autowired ItemService items;

    private int business(String name, int cityId, List<Integer> categoryIds) {
        String email = UUID.randomUUID() + "@example.com";
        long userId = registration.register(new RegisterRequest("Owner", email, "SecurePass123!", "+94771234567")).id();
        String uniqueName = "discover_" + UUID.randomUUID().toString().replace("-", "");
        var information = new BusinessInformationRequest(name, null, null, null, null, "123 Main Street", null,
                cityId, null, null, null, null, categoryIds);
        return businesses.register(userId, new RegisterBusinessRequest(uniqueName, information)).id();
    }

    private void item(long ownerId, String title, BigDecimal price, List<Integer> categoryIds) {
        items.create(ownerId, new CreateItemRequest(title, null, ItemType.PRODUCT, price, categoryIds));
    }

    @Test
    void descriptionSearchUsesLiteralCaseInsensitiveTextAndPreservesVisibility() throws Exception {
        int businessId = business("Description search", 1, List.of(8));
        String term = "only_" + UUID.randomUUID().toString().replace("-", "") + "%";
        jdbc.update("UPDATE business SET description=? WHERE id=?", term, businessId);
        long owner = jdbc.queryForObject("SELECT id FROM users WHERE business_id=?", Long.class, businessId);
        var item = items.create(owner, new CreateItemRequest("Unrelated title", term, ItemType.PRODUCT,
                BigDecimal.TEN, List.of(13)));
        for (String path : List.of("/api/v1/businesses", "/api/v1/items")) {
            mvc.perform(get(path).param("q", term.toUpperCase(java.util.Locale.ROOT)).param("cityId", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
            mvc.perform(get(path).param("q", term.replace("_", "X").replace("%", "X")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        mvc.perform(get("/api/v1/items").param("q", term).param("minPrice", "11"))
                .andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("UPDATE items SET status='INACTIVE' WHERE id=?", item.id());
        mvc.perform(get("/api/v1/items").param("q", term)).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("UPDATE items SET status='ACTIVE' WHERE id=?", item.id());
        jdbc.update("UPDATE business SET status='SUSPENDED' WHERE id=?", businessId);
        for (String path : List.of("/api/v1/businesses", "/api/v1/items")) {
            mvc.perform(get(path).param("q", term)).andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    @Test
    void exposesLocationHierarchyAndFiltersBusinessesByNameCategoryAndLocation() throws Exception {
        mvc.perform(get("/api/v1/locations/provinces")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(9));
        mvc.perform(get("/api/v1/locations/districts?provinceId=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].provinceName").value("Western"));
        mvc.perform(get("/api/v1/locations/cities?q=colombo&provinceId=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Colombo"))
                .andExpect(jsonPath("$.content[0].districtName").value("Colombo"));
        mvc.perform(get("/api/v1/locations/cities?districtId=1&provinceId=2")).andExpect(status().isBadRequest());

        int firstAlpha = business("Alpha Market", 1, List.of(8));
        int secondAlpha = business("Alpha Market", 1, List.of(8));
        business("Beta Market", 31, List.of(10));
        business("Inactive Market", 1, List.of(8));
        int legacyBusiness = business("Legacy Market", 1, List.of());
        jdbc.update("UPDATE business SET city_id=NULL, city='Oldtown', district='Old district', province='Old province' WHERE id=?", legacyBusiness);
        jdbc.update("UPDATE business SET status = 'SUSPENDED' WHERE name = 'Inactive Market'");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM business_category_assignments a JOIN business b ON b.id=a.business_id WHERE b.name=? AND a.category_id=8", Integer.class, "Alpha Market"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM business_category_assignments a JOIN business b ON b.id=a.business_id WHERE b.name=? AND a.category_id=10", Integer.class, "Beta Market"));

        mvc.perform(get("/api/v1/businesses?q=market"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        mvc.perform(get("/api/v1/businesses?q=market&categoryIds=8"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/businesses").param("q", " MaRkEt ").param("categoryIds", "8", "10")
                .param("sort", "name,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].information.name").value("Alpha Market"))
                .andExpect(jsonPath("$.content[1].information.name").value("Alpha Market"))
                .andExpect(jsonPath("$.content[2].information.name").value("Beta Market"))
                .andExpect(jsonPath("$.content[0].id").value(firstAlpha))
                .andExpect(jsonPath("$.content[1].id").value(secondAlpha));
        mvc.perform(get("/api/v1/businesses?q=market&cityId=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/businesses?q=legacy")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].information.city").value("Oldtown"))
                .andExpect(jsonPath("$.content[0].information.cityId").value(nullValue()));
        mvc.perform(get("/api/v1/businesses?q=legacy&provinceId=1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/businesses").param("q", "%")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/businesses?cityId=1&districtId=7")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/businesses?categoryIds=999")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/businesses?sort=description,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/businesses?page=1001&size=10")).andExpect(status().isBadRequest());
    }

    @Test
    void filtersAndSortsItemsWithExplicitNullPriceRulesAndBusinessLocation() throws Exception {
        int businessId = business("Item Discovery", 1, List.of(8));
        long ownerId = jdbc.queryForObject("SELECT u.id FROM users u WHERE u.business_id = ?", Long.class, businessId);
        item(ownerId, "Catalog item", null, List.of(13));
        item(ownerId, "Catalog item", BigDecimal.ZERO, List.of(25));
        item(ownerId, "Catalog item", new BigDecimal("10.00"), List.of(13));
        item(ownerId, "Catalog other item", new BigDecimal("20.00"), List.of(25));
        item(ownerId, "Catalog hidden item", new BigDecimal("5.00"), List.of(13));
        jdbc.update("UPDATE items SET status='INACTIVE' WHERE title='Catalog hidden item'");

        mvc.perform(get("/api/v1/items?maxPrice=10&categoryIds=13,25&cityId=1&sort=price,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].price").value(nullValue()))
                .andExpect(jsonPath("$.content[1].price").value(0))
                .andExpect(jsonPath("$.content[2].price").value(10));
        mvc.perform(get("/api/v1/items?q=catalog&minPrice=0&sort=price,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].price").value(0));
        mvc.perform(get("/api/v1/items?q=catalog&minPrice=1&sort=price,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].price").value(20))
                .andExpect(jsonPath("$.content[1].price").value(10));
        mvc.perform(get("/api/v1/items?q=catalog&sort=price,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].price").value(20))
                .andExpect(jsonPath("$.content[1].price").value(10))
                .andExpect(jsonPath("$.content[2].price").value(0))
                .andExpect(jsonPath("$.content[3].price").value(nullValue()));
        mvc.perform(get("/api/v1/items?q=catalog&categoryIds=13,25&cityId=1&type=PRODUCT&size=1&page=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/v1/items?minPrice=20&maxPrice=10")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items?minPrice=1.001")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items?sort=priceish,asc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items?page=2147483647&size=2147483647")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items")).andExpect(status().isOk());
        jdbc.update("UPDATE business SET status='SUSPENDED' WHERE id=?", businessId);
        mvc.perform(get("/api/v1/items?q=catalog")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        assertEquals(125, jdbc.queryForObject("SELECT count(*) FROM cities", Integer.class));
    }
}
