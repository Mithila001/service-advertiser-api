package mithila001.service_advertiser.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.common.PageResponse;

@Tag(name = "Locations", description = "Public province, district, and city catalogs for business locations.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {
    private final LocationCatalogService service;
    public LocationController(LocationCatalogService service) { this.service = service; }

    @Operation(summary = "List provinces", description =
            "Returns all provinces alphabetically.")
    @GetMapping("/provinces")
    public List<ProvinceResponse> provinces() { return service.provinces(); }

    @Operation(summary = "List districts", description =
            "Returns districts alphabetically, optionally filtered by an existing province.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping("/districts")
    public List<DistrictResponse> districts(
            @Parameter(description = "Filter by an existing catalog province ID.")
            @RequestParam(required = false) Integer provinceId) {
        return service.districts(provinceId);
    }

    @Operation(summary = "Search cities", description =
            "Returns cities ordered by name, then ID. District and province filters must belong to the same hierarchy.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping("/cities")
    public PageResponse<CityResponse> cities(
            @Parameter(description = "Case-insensitive city name substring; trimmed, maximum 150 characters. No wildcard matching.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by an existing catalog district ID.")
            @RequestParam(required = false) Integer districtId,
            @Parameter(description = "Filter by an existing catalog province ID.")
            @RequestParam(required = false) Integer provinceId,
            @Parameter(description = "Page number, starting at 0. See the pagination limits above.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Results per page; minimum 1. See the pagination defaults and limits above.")
            @RequestParam(required = false) Integer size) {
        return service.cities(q, districtId, provinceId, page, size);
    }
}
