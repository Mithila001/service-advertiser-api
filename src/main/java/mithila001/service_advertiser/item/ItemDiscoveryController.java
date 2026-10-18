package mithila001.service_advertiser.item;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.item.dto.ItemResponse;

@RestController
@RequestMapping("/api/v1/items")
public class ItemDiscoveryController {
    private final ItemDiscoveryService service;
    public ItemDiscoveryController(ItemDiscoveryService service) { this.service = service; }

    @GetMapping
    public PageResponse<ItemResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) List<Integer> categoryIds,
            @RequestParam(required = false) Integer cityId, @RequestParam(required = false) Integer districtId,
            @RequestParam(required = false) Integer provinceId, @RequestParam(required = false) ItemType type,
            @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, type, minPrice, maxPrice, sort, page, size);
    }
}
