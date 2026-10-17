package mithila001.service_advertiser.item.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import mithila001.service_advertiser.item.ItemType;

public record CreateItemRequest(@NotBlank @Size(max = 150) String title, String description,
        @NotNull ItemType type, @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal price) {}
