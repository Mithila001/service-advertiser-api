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

@Tag(name = "Businesses", description = "Public business profiles and your business.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessDiscoveryController {
    private final BusinessDiscoveryService service;
    public BusinessDiscoveryController(BusinessDiscoveryService service) { this.service = service; }

    @Operation(summary = "Search businesses", description =
            "Returns ACTIVE businesses. Different filters combine with AND. " +
            "Location IDs must belong to the same hierarchy.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping
    public PageResponse<BusinessResponse> search(
            @Parameter(description = "Case-insensitive business name substring; trimmed, maximum 150 characters. No wildcard matching.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Matches any selected category. Use unique, positive catalog IDs, comma-separated or repeated. Omit for all categories.")
            @RequestParam(required = false) List<Integer> categoryIds,
            @Parameter(description = "Filter by an existing catalog city ID.")
            @RequestParam(required = false) Integer cityId,
            @Parameter(description = "Filter by an existing catalog district ID.")
            @RequestParam(required = false) Integer districtId,
            @Parameter(description = "Filter by an existing catalog province ID.")
            @RequestParam(required = false) Integer provinceId,
            @Parameter(description = "Sort by business name. Default: name,asc. ID breaks ties.",
                    schema = @Schema(allowableValues = {"name,asc", "name,desc"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Page number, starting at 0. See the pagination limits above.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Results per page; minimum 1. See the pagination defaults and limits above.")
            @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, sort, page, size);
    }
}
