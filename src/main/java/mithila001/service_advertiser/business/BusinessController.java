package mithila001.service_advertiser.business;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.business.dto.*;

@Tag(name = "Businesses", description = "Public business profiles and your business.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {
    private final BusinessService service;

    public BusinessController(BusinessService service) { this.service = service; }

    @Operation(summary = "Register your business", description =
            "Requires an active CUSTOMER with no business. Creates an ACTIVE business and changes your role " +
            "to PROVIDER. Refresh or log in to update your token's role.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Business created"),
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping
    public ResponseEntity<BusinessResponse> register(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterBusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(Long.valueOf(jwt.getSubject()), request));
    }

    @Operation(summary = "Get your business", description =
            "Returns your business, even when suspended. Requires an active account that owns a business.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @GetMapping("/me")
    public BusinessResponse own(@AuthenticationPrincipal Jwt jwt) {
        return service.own(Long.valueOf(jwt.getSubject()));
    }

    @Operation(summary = "Replace your business information", description =
            "Requires an active account and an ACTIVE owned business. Omitted optional fields and categories " +
            "are cleared. The unique name cannot change.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @PutMapping("/me")
    public BusinessResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessInformationRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), request);
    }

    @Operation(summary = "Delete your business", description =
            "Permanently deletes the ACTIVE owned business and all its items. Retains the user account and " +
            "sessions, and changes the role to CUSTOMER.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Business deleted; no response body", content = @Content),
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
        @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        service.delete(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get a public business profile", description =
            "Returns an ACTIVE business. Unknown or inactive businesses return 404.")
    @ApiResponses({
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @GetMapping("/{uniqueName}")
    public BusinessResponse publicProfile(@PathVariable String uniqueName) {
        return service.publicProfile(uniqueName);
    }
}
