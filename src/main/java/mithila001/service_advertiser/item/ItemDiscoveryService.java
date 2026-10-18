package mithila001.service_advertiser.item;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.business.BusinessDiscoveryService;
import mithila001.service_advertiser.category.ItemCategoryRepository;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.common.PaginationPolicy;
import mithila001.service_advertiser.item.dto.ItemResponse;
import mithila001.service_advertiser.location.LocationCatalogService;

@Service
@Transactional(readOnly = true)
public class ItemDiscoveryService {
    private final ItemRepository items;
    private final ItemCategoryRepository categories;
    private final LocationCatalogService locations;
    private final PaginationPolicy pagination;

    public ItemDiscoveryService(ItemRepository items, ItemCategoryRepository categories,
            LocationCatalogService locations, PaginationPolicy pagination) {
        this.items = items;
        this.categories = categories;
        this.locations = locations;
        this.pagination = pagination;
    }

    public PageResponse<ItemResponse> search(String query, List<Integer> categoryIds, Integer cityId,
            Integer districtId, Integer provinceId, ItemType type, BigDecimal minPrice,
            BigDecimal maxPrice, String sort, Integer page, Integer size) {
        String text = query == null ? "" : query.trim();
        if (text.length() > 150) throw badRequest("Search text must be at most 150 characters");
        List<Integer> ids = categoryIds == null ? List.of() : new ArrayList<>(categoryIds);
        BusinessDiscoveryService.validateIds(ids, ids.isEmpty() ? 0 : categories.countByIdIn(ids), "item category");
        locations.validateSelection(cityId, districtId, provinceId);
        if ((minPrice != null && minPrice.signum() < 0) || (maxPrice != null && maxPrice.signum() < 0)
                || (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
                || hasSubcent(minPrice) || hasSubcent(maxPrice)) throw badRequest("Invalid LKR price range");
        Sort.Direction direction = BusinessDiscoveryService.direction(sort, true);
        String field = BusinessDiscoveryService.sortField(sort);
        var pageable = pagination.page(page, size, Sort.unsorted());
        Page<Item> result = items.findAll(ItemDiscovery.filter(text.toLowerCase(Locale.ROOT), ids, cityId,
                districtId, provinceId, type, minPrice, maxPrice, field, direction), pageable);
        return PageResponse.from(result, ItemResponse::from);
    }

    private static boolean hasSubcent(BigDecimal value) {
        return value != null && value.stripTrailingZeros().scale() > 2;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
