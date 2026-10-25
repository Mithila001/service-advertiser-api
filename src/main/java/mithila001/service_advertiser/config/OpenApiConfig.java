package mithila001.service_advertiser.config;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import mithila001.service_advertiser.common.exception.GlobalExceptionHandler.ErrorResponse;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI serviceAdvertiserApi() {
        var components = new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Paste your accessToken without the Bearer prefix."));
        for (HttpStatus status : new HttpStatus[] { HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.CONTENT_TOO_LARGE, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.INTERNAL_SERVER_ERROR }) {
            components.addResponses("Error" + status.value(), new ApiResponse().description(switch (status) {
                case BAD_REQUEST -> "Invalid request data or catalog IDs.";
                case UNAUTHORIZED -> "Missing or invalid access token, credentials, or refresh token.";
                case FORBIDDEN -> "Your account, role, or business does not permit this action.";
                case NOT_FOUND -> "Resource not found or not accessible to you.";
                case CONFLICT -> "Duplicate registration, item limit reached, or incompatible business state.";
                case CONTENT_TOO_LARGE -> "JSON body exceeds the configured byte limit.";
                case TOO_MANY_REQUESTS -> "Request rate exceeded. Retry-After gives the wait in seconds.";
                case SERVICE_UNAVAILABLE -> "Service temporarily unavailable; do not automatically retry writes.";
                default -> "Unexpected server error.";
            }).content(new Content().addMediaType("application/json", new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        }
        components.getResponses().get("Error429").addHeaderObject("Retry-After",
                new io.swagger.v3.oas.models.headers.Header().description("Seconds to wait before another attempt")
                        .schema(new Schema<Integer>().type("integer").minimum(java.math.BigDecimal.ONE)));
        return new OpenAPI().components(components).info(new Info().title("Service Advertiser API").version("v1")
                .description("""
                        Discover businesses, products, and services, and manage your own business and items.

                        **Authentication:** Log in at `POST /api/v1/auth/login`, then paste `accessToken`
                        into **Authorize** without the `Bearer` prefix. Public search and catalogs need no token.

                        **Pagination:** `page` defaults to 0. `size` defaults to 10 and must be between 1 and 100.
                        `page * size` must not exceed 10000. These defaults and limits are deployment configurable.

                        **Errors:** JSON errors include `status`, `error`, `message`, and `fieldErrors`.
                        Check `fieldErrors` for validation details.
                        """));
    }

    @Bean
    OpenApiCustomizer errorResponseSchema() {
        return api -> {
            ModelConverters.getInstance(true).readAll(ErrorResponse.class).forEach(api.getComponents()::addSchemas);
            api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                operation.getResponses().addApiResponse("503", new ApiResponse().$ref("#/components/responses/Error503"));
                if (operation.getRequestBody() != null)
                    operation.getResponses().addApiResponse("413", new ApiResponse().$ref("#/components/responses/Error413"));
                if (java.util.Set.of("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh").contains(path))
                    operation.getResponses().addApiResponse("429", new ApiResponse().$ref("#/components/responses/Error429"));
            }));
        };
    }
}
