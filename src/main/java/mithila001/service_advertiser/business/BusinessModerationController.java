package mithila001.service_advertiser.business;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.business.dto.BusinessModerationRequest;
import mithila001.service_advertiser.business.dto.BusinessModerationResponse;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;

@Tag(name = "Business moderation", description = "Requires an active ADMIN account and an access JWT with the ADMIN role.")
@ApiResponses({
    @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/Error403"),
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/businesses")
@PreAuthorize("hasRole('ADMIN')")
public class BusinessModerationController {
    private final BusinessModerationService service;

    public BusinessModerationController(BusinessModerationService service) { this.service = service; }

    @Operation(summary = "List suspended businesses", description =
            "Paged results ordered by updatedAt descending, then ID descending.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping("/suspended")
    public PageResponse<BusinessResponse> suspended(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Zero-based page number; defaults to 0. page * size must not exceed the configured maximum offset (default 10000).")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size; defaults to the configured size (10), with a configured maximum (100). Minimum 1.")
            @RequestParam(required = false) Integer size) {
        return service.suspended(Long.valueOf(jwt.getSubject()), page, size);
    }

    @Operation(summary = "Get business moderation history", description =
            "Returns the audit history for an existing business, ordered by creation time descending, then ID descending.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404")
    })
    @GetMapping("/{businessId}/history")
    public List<BusinessModerationResponse> history(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer businessId) {
        return service.history(Long.valueOf(jwt.getSubject()), businessId);
    }

    @Operation(summary = "Suspend a business", description =
            "Changes an ACTIVE business to SUSPENDED and records an audit event. Request body and reason are " +
            "optional. Public profiles and items become hidden; owner writes are blocked.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping("/{businessId}/suspend")
    public BusinessResponse suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer businessId,
            @Valid @RequestBody(required = false) BusinessModerationRequest request) {
        return service.suspend(Long.valueOf(jwt.getSubject()), businessId, request == null ? null : request.reason());
    }

    @Operation(summary = "Reactivate a suspended business", description =
            "Changes a SUSPENDED business to ACTIVE and records an audit event. Request body and reason are " +
            "optional.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
        @ApiResponse(responseCode = "404", ref = "#/components/responses/Error404"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping("/{businessId}/activate")
    public BusinessResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer businessId,
            @Valid @RequestBody(required = false) BusinessModerationRequest request) {
        return service.activate(Long.valueOf(jwt.getSubject()), businessId, request == null ? null : request.reason());
    }
}
