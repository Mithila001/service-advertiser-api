package mithila001.service_advertiser.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.*;
import mithila001.service_advertiser.item.ItemType;

@Schema(description = "Product or service details. Price is in LKR; null or omitted means unspecified, 0 means free. Use unique, positive item category IDs; default maximum 2 (configurable).",
        example = "{\"title\":\"Home cleaning\",\"description\":\"Two hours\",\"type\":\"SERVICE\",\"price\":2500.00,\"categoryIds\":[]}")
public record CreateItemRequest(@NotBlank @Size(max = 150) String title, @Size(max = 10000) String description,
        @NotNull ItemType type, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal price,
        List<@NotNull @Positive Integer> categoryIds) {
    public CreateItemRequest(String title, String description, ItemType type, BigDecimal price) {
        this(title, description, type, price, null);
    }
}
