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

@Tag(name = "Items", description = "Public products and services, and your business's items.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/items")
public class ItemDiscoveryController {
    private final ItemDiscoveryService service;
    public ItemDiscoveryController(ItemDiscoveryService service) { this.service = service; }

    @Operation(summary = "Search items", description =
            "Returns ACTIVE items from ACTIVE businesses. Different filters combine with AND. " +
            "Location IDs must belong to the same hierarchy.")
    @ApiResponses({
        @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400")
    })
    @GetMapping
    public PageResponse<ItemResponse> search(
            @Parameter(description = "Case-insensitive title or description substring; trimmed, maximum 150 characters. No wildcard matching.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Matches any selected category. Use unique, positive catalog IDs, comma-separated or repeated. Omit for all categories.")
            @RequestParam(required = false) List<Integer> categoryIds,
            @Parameter(description = "Filter by an existing catalog city ID.")
            @RequestParam(required = false) Integer cityId,
            @Parameter(description = "Filter by an existing catalog district ID.")
            @RequestParam(required = false) Integer districtId,
            @Parameter(description = "Filter by an existing catalog province ID.")
            @RequestParam(required = false) Integer provinceId,
            @Parameter(description = "Filter products or services.")
            @RequestParam(required = false) ItemType type,
            @Parameter(description = "Minimum price in LKR, inclusive; nonnegative, up to 2 decimal places. Excludes unpriced items, including when set to 0.")
            @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price in LKR, inclusive; nonnegative, up to 2 decimal places, at least minPrice. Includes unpriced items if minPrice is omitted.")
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Default: name,asc (item title). ID breaks ties. Unpriced items sort first for price,asc and last for price,desc.",
                    schema = @Schema(allowableValues = {"name,asc", "name,desc", "price,asc", "price,desc"}))
            @RequestParam(required = false) String sort,
            @Parameter(description = "Page number, starting at 0. See the pagination limits above.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Results per page; minimum 1. See the pagination defaults and limits above.")
            @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, type, minPrice, maxPrice, sort, page, size);
    }
}
