package mithila001.service_advertiser.item.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import mithila001.service_advertiser.item.*;

public record ItemResponse(Long id, Integer businessId, String businessUniqueName, String businessName, String title, String description, ItemType type,
        BigDecimal price, ItemStatus status, Instant createdAt, Instant updatedAt, List<Integer> categoryIds) {
    public static ItemResponse from(Item item) {
        return from(item, item.getBusiness());
    }

    public static ItemResponse from(Item item, mithila001.service_advertiser.business.Business business) {
        return new ItemResponse(item.getId(), item.getBusinessId(), business.getUniqueName(), business.getName(), item.getTitle(), item.getDescription(),
                item.getType(), item.getPrice(), item.getStatus(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getCategories().stream().map(category -> category.getId()).sorted().toList());
    }
}
