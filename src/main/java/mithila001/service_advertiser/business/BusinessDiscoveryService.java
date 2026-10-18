package mithila001.service_advertiser.business;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.category.BusinessCategoryRepository;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.common.PaginationPolicy;
import mithila001.service_advertiser.location.LocationCatalogService;

@Service
@Transactional(readOnly = true)
public class BusinessDiscoveryService {
    private final BusinessRepository businesses;
    private final BusinessCategoryRepository categories;
    private final LocationCatalogService locations;
    private final PaginationPolicy pagination;

    public BusinessDiscoveryService(BusinessRepository businesses, BusinessCategoryRepository categories,
            LocationCatalogService locations, PaginationPolicy pagination) {
        this.businesses = businesses;
        this.categories = categories;
        this.locations = locations;
        this.pagination = pagination;
    }

    public PageResponse<BusinessResponse> search(String query, List<Integer> categoryIds, Integer cityId,
            Integer districtId, Integer provinceId, String sort, Integer page, Integer size) {
        String text = query == null ? "" : query.trim();
        if (text.length() > 150) throw badRequest("Search text must be at most 150 characters");
        List<Integer> ids = categoryIds == null ? List.of() : new ArrayList<>(categoryIds);
        validateIds(ids, ids.isEmpty() ? 0 : categories.countByIdIn(ids), "business category");
        locations.validateSelection(cityId, districtId, provinceId);
        Sort.Direction direction = direction(sort, false);
        var pageable = pagination.page(page, size, Sort.unsorted());
        Page<Business> result = businesses.findAll(
                BusinessDiscovery.filter(text.toLowerCase(Locale.ROOT), ids, cityId, districtId, provinceId, direction), pageable);
        return PageResponse.from(result, BusinessResponse::from);
    }

    public static Sort.Direction direction(String sort, boolean priceAllowed) {
        if (sort == null || sort.isBlank()) return Sort.Direction.ASC;
        String[] parts = sort.trim().toLowerCase(Locale.ROOT).split(",", -1);
        if (parts.length != 2 || !(parts[0].equals("name") || priceAllowed && parts[0].equals("price"))) {
            throw badRequest("Unsupported sort");
        }
        try { return Sort.Direction.fromString(parts[1]); }
        catch (IllegalArgumentException exception) { throw badRequest("Unsupported sort"); }
    }

    public static String sortField(String sort) {
        if (sort == null || sort.isBlank()) return "name";
        String field = sort.trim().toLowerCase(Locale.ROOT).split(",", -1)[0];
        if (!field.equals("name") && !field.equals("price")) throw badRequest("Unsupported sort");
        return field;
    }

    public static void validateIds(List<Integer> ids, long knownCount, String kind) {
        if (ids.stream().anyMatch(id -> id == null || id < 1) || ids.stream().distinct().count() != ids.size()
                || ids.size() != knownCount) throw badRequest("Invalid " + kind + " IDs");
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
