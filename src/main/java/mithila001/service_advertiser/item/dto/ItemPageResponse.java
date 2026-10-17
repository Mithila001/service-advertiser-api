package mithila001.service_advertiser.item.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import mithila001.service_advertiser.item.Item;

public record ItemPageResponse(List<ItemResponse> content, int page, int size, long totalElements, int totalPages) {
    public static ItemPageResponse from(Page<Item> items) {
        return new ItemPageResponse(items.getContent().stream().map(ItemResponse::from).toList(),
                items.getNumber(), items.getSize(), items.getTotalElements(), items.getTotalPages());
    }
}
