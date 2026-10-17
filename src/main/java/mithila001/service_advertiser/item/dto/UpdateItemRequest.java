package mithila001.service_advertiser.item.dto;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.*;
import mithila001.service_advertiser.item.*;

public class UpdateItemRequest {
    @Size(max = 150) @Pattern(regexp = "(?s).*\\S.*")
    private String title;
    private String description;
    private ItemType type;
    @DecimalMin("0") @Digits(integer = 17, fraction = 2)
    private BigDecimal price;
    private ItemStatus status;
    private boolean descriptionPresent;
    private boolean pricePresent;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; descriptionPresent = true; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setType(ItemType type) { this.type = type; }
    public void setPrice(BigDecimal price) { this.price = price; pricePresent = true; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setStatus(ItemStatus status) { this.status = status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItemType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public ItemStatus getStatus() { return status; }
    public boolean hasDescription() { return descriptionPresent; }
    public boolean hasPrice() { return pricePresent; }
}
