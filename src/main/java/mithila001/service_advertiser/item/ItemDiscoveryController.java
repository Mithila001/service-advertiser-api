package mithila001.service_advertiser.item;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.item.dto.ItemResponse;

@Tag(name = "Items", description = "Public products and services, and management of the current business's items.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/items")
public class ItemDiscoveryController {
    private final ItemDiscoveryService service;
    public ItemDiscoveryController(ItemDiscoveryService service) { this.service = service; }

    @Operation(summary = "Search items", description =
            "Public search of ACTIVE items belonging to ACTIVE businesses. q is a trimmed, case-insensitive " +
            "literal title substring (maximum 150 characters). Categories match ANY selected ID; other " +
            "filters combine with AND. Location IDs must exist and form a consistent hierarchy. Default sort " +
            "is name,asc (item title), with ID breaking ties. Unpriced items sort first for ascending price " +
            "and last for descending price. Any minPrice, including zero, excludes unpriced items; a maxPrice " +
            "alone includes them.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping
    public PageResponse<ItemResponse> search(
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
            @Parameter(description = "Filter products or services.")
            @RequestParam(required = false) ItemType type,
            @Parameter(description = "Inclusive minimum price in LKR, nonnegative, at most two fractional digits. Excludes unpriced items, even when zero.")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Inclusive maximum price in LKR, nonnegative, at most two fractional digits; must be at least minPrice. Includes unpriced items when minPrice is omitted.")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Sort field and direction; defaults to name,asc. For items, name sorts by title.", schema = @Schema(allowableValues = {"name,asc", "name,desc", "price,asc", "price,desc"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Zero-based page number; defaults to 0. page * size must not exceed the configured maximum offset (default 10000).")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size; defaults to the configured size (10), with a configured maximum (100). Minimum 1.")
            @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, type, minPrice, maxPrice, sort, page, size);
    }
}
