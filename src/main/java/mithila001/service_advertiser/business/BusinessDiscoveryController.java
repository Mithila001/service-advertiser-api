package mithila001.service_advertiser.business;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;

@Tag(name = "Businesses", description = "Public business profiles and management of the current account's business.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessDiscoveryController {
    private final BusinessDiscoveryService service;
    public BusinessDiscoveryController(BusinessDiscoveryService service) { this.service = service; }

    @Operation(summary = "Search businesses", description =
            "Public search of ACTIVE businesses. q is a trimmed, case-insensitive literal substring of the " +
            "name (maximum 150 characters). Categories match ANY selected ID; other filters combine with AND. " +
            "Location IDs must exist and describe a consistent hierarchy. Default sort is name,asc; ID breaks " +
            "ties.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping
    public PageResponse<BusinessResponse> search(
            @Parameter(description = "Trimmed, case-insensitive literal substring; at most 150 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Match ANY selected category. IDs must be known, positive, and unique. Supply comma-separated IDs or repeat the parameter; omit for all categories.")
            @RequestParam(required = false) List<Integer> categoryIds,
            @Parameter(description = "Filter by catalog city ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer cityId,
            @Parameter(description = "Filter by catalog district ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer districtId,
            @Parameter(description = "Filter by catalog province ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer provinceId,
            @Parameter(description = "Sort by business name; defaults to name,asc.",
                    schema = @Schema(allowableValues = {"name,asc", "name,desc"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Zero-based page number; defaults to 0. page * size must not exceed the configured maximum offset (default 10000).")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size; defaults to the configured size (10), with a configured maximum (100). Minimum 1.")
            @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, sort, page, size);
    }
}
