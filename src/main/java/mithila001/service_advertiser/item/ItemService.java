package mithila001.service_advertiser.item;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.business.*;
import mithila001.service_advertiser.item.dto.*;

@Service
@Transactional
public class ItemService {
    private final ItemRepository items;
    private final BusinessService businesses;
    private final Clock clock;
    private final int maxPerBusiness;

    public ItemService(ItemRepository items, BusinessService businesses, Clock clock,
            @Value("${app.items.max-per-business:10}") int maxPerBusiness) {
        if (maxPerBusiness < 1) throw new IllegalArgumentException("Item limit must be positive");
        this.items = items;
        this.businesses = businesses;
        this.clock = clock;
        this.maxPerBusiness = maxPerBusiness;
    }

    public ItemResponse create(Long userId, CreateItemRequest request) {
        Business business = mutableBusiness(userId);
        // The owner's user-row lock is held until commit, serializing count + insert and business deletion.
        if (items.countByBusinessId(business.getId()) >= maxPerBusiness) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business item limit reached");
        }
        return ItemResponse.from(items.save(new Item(business.getId(), request, LocalDateTime.now(clock))));
    }

    public List<ItemResponse> own(Long userId) {
        Business business = businesses.providerBusiness(userId);
        return items.findByBusinessIdOrderByIdAsc(business.getId()).stream().map(ItemResponse::from).toList();
    }

    public ItemResponse update(Long userId, Long id, UpdateItemRequest request) {
        Item item = ownedItem(id, mutableBusiness(userId));
        item.update(request, LocalDateTime.now(clock));
        return ItemResponse.from(item);
    }

    public void delete(Long userId, Long id) {
        items.delete(ownedItem(id, mutableBusiness(userId)));
    }

    @Transactional(readOnly = true)
    public ItemResponse publicItem(Long id) {
        return ItemResponse.from(items.findPublicById(id).orElseThrow(ItemService::notFound));
    }

    @Transactional(readOnly = true)
    public ItemPageResponse publicItems(String uniqueName, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        return ItemPageResponse.from(items.findPublicByBusiness(uniqueName, PageRequest.of(page, size, Sort.by("id"))));
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
