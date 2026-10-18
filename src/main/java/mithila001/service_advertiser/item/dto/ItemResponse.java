package mithila001.service_advertiser.item.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import mithila001.service_advertiser.item.*;

public record ItemResponse(Long id, Integer businessId, String title, String description, ItemType type,
        BigDecimal price, ItemStatus status, LocalDateTime createdAt, LocalDateTime updatedAt, List<Integer> categoryIds) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getBusinessId(), item.getTitle(), item.getDescription(),
                item.getType(), item.getPrice(), item.getStatus(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getCategories().stream().map(category -> category.getId()).sorted().toList());
    }
}
