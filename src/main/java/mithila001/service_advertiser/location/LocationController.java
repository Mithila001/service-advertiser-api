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
            @Parameter(description = "Filter by catalog province ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer provinceId) {
        return service.districts(provinceId);
    }

    @Operation(summary = "Search cities", description =
            "Case-insensitive literal substring search of city names, up to 150 characters after trimming. " +
            "Optional district and province must exist and belong to the same hierarchy. Results are ordered " +
            "by name, then ID.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping("/cities")
    public PageResponse<CityResponse> cities(
            @Parameter(description = "Trimmed, case-insensitive literal substring; at most 150 characters.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by catalog district ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer districtId,
            @Parameter(description = "Filter by catalog province ID. Combined location filters must describe the same hierarchy.")
            @RequestParam(required = false) Integer provinceId,
            @Parameter(description = "Zero-based page number; defaults to 0. page * size must not exceed the configured maximum offset (default 10000).")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size; defaults to the configured size (10), with a configured maximum (100). Minimum 1.")
            @RequestParam(required = false) Integer size) {
        return service.cities(q, districtId, provinceId, page, size);
    }
}
