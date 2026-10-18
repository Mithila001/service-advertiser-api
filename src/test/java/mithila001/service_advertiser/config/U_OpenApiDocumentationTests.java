package mithila001.service_advertiser.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springdoc.webmvc.ui.SwaggerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import mithila001.service_advertiser.auth.AuthenticationService;
import mithila001.service_advertiser.auth.RegistrationService;
import mithila001.service_advertiser.business.BusinessDiscoveryService;
import mithila001.service_advertiser.business.BusinessModerationService;
import mithila001.service_advertiser.business.BusinessService;
import mithila001.service_advertiser.category.CategoryService;
import mithila001.service_advertiser.item.ItemDiscoveryService;
import mithila001.service_advertiser.item.ItemService;
import mithila001.service_advertiser.location.LocationCatalogService;

@WebMvcTest(properties = { "springdoc.paths-to-match=/api/v1/**", "springdoc.override-with-generic-response=false",
        "springdoc.default-produces-media-type=application/json" })
@Import({ OpenApiConfig.class, SecurityConfig.class })
@ImportAutoConfiguration({ SpringDocConfiguration.class, SpringDocConfigProperties.class,
        SpringDocSecurityConfiguration.class, SpringDocWebMvcConfiguration.class, SwaggerConfig.class,
        SwaggerUiConfigProperties.class, SwaggerUiOAuthProperties.class })
class U_OpenApiDocumentationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AuthenticationService authentication;
    @MockitoBean RegistrationService registration;
    @MockitoBean BusinessService businesses;
    @MockitoBean BusinessDiscoveryService businessDiscovery;
    @MockitoBean BusinessModerationService moderation;
    @MockitoBean ItemService items;
    @MockitoBean ItemDiscoveryService itemDiscovery;
    @MockitoBean CategoryService categories;
    @MockitoBean LocationCatalogService locations;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void generatedSpecificationDocumentsThePublicAndProtectedContracts() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var document = mapper.readTree(result.getResponse().getContentAsString());
        assertEquals("Service Advertiser API", document.at("/info/title").stringValue());
        assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").stringValue());
        assertTrue(document.path("security").isMissingNode(), "Public endpoints must not inherit authentication");

        int operationCount = 0;
        for (var path : document.path("paths").properties()) {
            assertTrue(path.getKey().startsWith("/api/v1/"));
            for (var method : path.getValue().properties()) {
                operationCount++;
                var operation = method.getValue();
                assertFalse(operation.path("summary").stringValue().isBlank(), path.getKey());
                assertFalse(operation.path("description").stringValue().isBlank(), path.getKey());
                assertFalse(operation.path("tags").isEmpty(), path.getKey());
                assertTrue(operation.path("responses").has("500"), path.getKey());
                boolean protectedOperation = path.getKey().startsWith("/api/v1/admin/")
                        || path.getKey().contains("/me")
                        || path.getKey().equals("/api/v1/businesses") && method.getKey().equals("post")
                        || path.getKey().equals("/api/v1/items/{id}") && !method.getKey().equals("get");
                if (protectedOperation) {
                    assertTrue(operation.at("/security/0").has("bearerAuth"), path.getKey());
                    assertTrue(operation.path("responses").has("401"), path.getKey());
                    assertTrue(operation.path("responses").has("403"), path.getKey());
                } else {
                    assertTrue(operation.path("security").isMissingNode()
                            || operation.path("security").isEmpty(), path.getKey());
                }
                for (var parameter : operation.path("parameters")) {
                    assertNotEquals("jwt", parameter.path("name").stringValue());
                    if (parameter.path("in").stringValue().equals("query")) {
                        assertFalse(parameter.path("description").stringValue().isBlank());
                    }
                }
            }
        }
        assertEquals(26, operationCount);
        for (String path : new String[] { "/api/v1/auth/register", "/api/v1/businesses",
                "/api/v1/businesses/me/items" }) {
            var responses = document.path("paths").path(path).path("post").path("responses");
            assertTrue(responses.has("201"), path);
            assertFalse(responses.has("200"), path);
            assertTrue(responses.path("201").path("content").has("application/json"), path);
        }
        assertFalse(document.path("paths").path("/api/v1/items/{id}").path("delete")
                .at("/responses/204").has("content"));
        assertEquals("#/components/schemas/ErrorResponse", document.at(
                "/components/responses/Error400/content/application~1json/schema/$ref").stringValue());
        assertTrue(document.at("/components/schemas/ErrorResponse/properties").has("fieldErrors"));
        assertEquals("string", document.at(
                "/components/schemas/ErrorResponse/properties/fieldErrors/additionalProperties/type").stringValue());
        assertTrue(document.at("/components/schemas/UpdateItemRequest/description").stringValue()
                .contains("Explicit null clears"));
        assertTrue(document.at("/components/schemas/UpdateItemRequest/properties/price/type").toString()
                .contains("null"));
        assertTrue(document.at("/components/schemas/RegisterRequest/properties/password/writeOnly").asBoolean());
    }

    @Test
    void swaggerUiAndYamlAreAccessibleWithoutAuthentication() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/businesses/me")).andExpect(status().isUnauthorized());
    }
}
