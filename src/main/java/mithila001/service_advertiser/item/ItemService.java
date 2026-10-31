package mithila001.service_advertiser.item;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.category.CategoryService;
import mithila001.service_advertiser.business.*;
import mithila001.service_advertiser.item.dto.*;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.common.PaginationPolicy;

@Service
@Transactional
public class ItemService {
    private final ItemRepository items;
    private final BusinessService businesses;
    private final CategoryService categories;
    private final Clock clock;
    private final int maxPerBusiness;
    private final PaginationPolicy pagination;

    public ItemService(ItemRepository items, BusinessService businesses, CategoryService categories, Clock clock,
            @Value("${app.items.max-per-business:10}") int maxPerBusiness, PaginationPolicy pagination) {
        if (maxPerBusiness < 1) throw new IllegalArgumentException("Item limit must be positive");
        this.items = items;
        this.businesses = businesses;
        this.categories = categories;
        this.clock = clock;
        this.maxPerBusiness = maxPerBusiness;
        this.pagination = pagination;
    }

    public ItemResponse create(Long userId, CreateItemRequest request) {
        Business business = mutableBusiness(userId);
        // The owner's user-row lock is held until commit, serializing count + insert and business deletion.
        if (items.countByBusinessId(business.getId()) >= maxPerBusiness) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business item limit reached");
        }
        Item item = new Item(business.getId(), request, clock.instant());
        item.setCategories(categories.itemSelection(request.categoryIds()));
        return ItemResponse.from(items.save(item), business);
    }

    public List<ItemResponse> own(Long userId) {
        Business business = businesses.providerBusiness(userId);
        return items.findByBusinessIdOrderByIdAsc(business.getId()).stream().map(item -> ItemResponse.from(item, business)).toList();
    }

    public ItemResponse update(Long userId, Long id, UpdateItemRequest request) {
        Business business = mutableBusiness(userId);
        Item item = ownedItem(id, business);
        item.update(request, clock.instant());
        if (request.hasCategoryIds()) item.setCategories(categories.itemSelection(request.getCategoryIds()));
        return ItemResponse.from(item, business);
    }

    public void delete(Long userId, Long id) {
        items.delete(ownedItem(id, mutableBusiness(userId)));
    }

    @Transactional(readOnly = true)
    public ItemResponse publicItem(Long id) {
        return ItemResponse.from(items.findPublicById(id).orElseThrow(ItemService::notFound));
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> publicItems(String uniqueName, Integer page, Integer size) {
        Pageable pageable = pagination.page(page, size, Sort.by("id"));
        return PageResponse.from(items.findPublicByBusiness(uniqueName, pageable), ItemResponse::from);
    }

    private Business mutableBusiness(Long userId) {
        Business business = businesses.providerBusiness(userId);
        if (business.getStatus() != BusinessStatus.ACTIVE) {
            throw new AccessDeniedException("Business is inactive");
        }
        return business;
    }

    private Item ownedItem(Long id, Business business) {
        return items.findByIdAndBusinessId(id, business.getId()).orElseThrow(ItemService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found");
    }
}
