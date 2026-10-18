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
                .description("Log in and paste the accessToken here, without the Bearer prefix."));
        for (HttpStatus status : new HttpStatus[] { HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED,
                HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND, HttpStatus.CONFLICT, HttpStatus.INTERNAL_SERVER_ERROR }) {
            components.addResponses("Error" + status.value(), new ApiResponse().description(switch (status) {
                case BAD_REQUEST -> "Invalid JSON, request fields, query parameters, or catalog selection.";
                case UNAUTHORIZED -> "Missing or invalid access token, credentials, or refresh token.";
                case FORBIDDEN -> "Account state, role, ownership, or business state does not permit this action.";
                case NOT_FOUND -> "The requested resource is unavailable or does not belong to the current owner.";
                case CONFLICT -> "Duplicate registration, item limit reached, or incompatible business state.";
                default -> "Unexpected server error.";
            }).content(new Content().addMediaType("application/json", new MediaType()
                    .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        }
        return new OpenAPI().components(components).info(new Info().title("Service Advertiser API").version("v1")
                .description("""
                        Discover businesses, products, and services, and manage your own business and items.

                        **Authentication:** Register an account, then log in at `POST /api/v1/auth/login`.
                        Copy `accessToken` into **Authorize** without the `Bearer` prefix. Lock icons mark
                        protected operations. Public discovery and catalogs need no token. Moderation requires
                        an active ADMIN account. Refresh rotates both tokens; replace both after each refresh.
                        Reusing a consumed refresh token revokes its session. Logout revokes refresh tokens;
                        an existing access token remains valid until expiry.

                        **Pagination:** `page` starts at 0. Default size is 10, maximum size is 100,
                        and `page * size` must not exceed 10000 by default. Deployment settings can change
                        these limits. Paged responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`.

                        **Errors:** JSON errors contain `status`, `error`, `message`, and `fieldErrors`.
                        Validation errors may include field-specific messages in `fieldErrors`.
                        Prices are in Sri Lankan rupees (LKR); null means unspecified, and zero means free.
                        """));
    }

    @Bean
    OpenApiCustomizer errorResponseSchema() {
        return api -> ModelConverters.getInstance(true).readAll(ErrorResponse.class)
                .forEach(api.getComponents()::addSchemas);
    }
}
