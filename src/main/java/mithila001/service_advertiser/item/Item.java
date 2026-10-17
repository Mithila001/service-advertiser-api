package mithila001.service_advertiser.item;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import mithila001.service_advertiser.item.dto.*;

@Entity
@Table(name = "items")
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private Integer businessId;
    @Column(nullable = false, length = 150)
    private String title;
    @Column(columnDefinition = "text")
    private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ItemType type;
    @Column(precision = 19, scale = 2)
    private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ItemStatus status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Item() {}
    public Item(Integer businessId, CreateItemRequest request, LocalDateTime now) {
        this.businessId = businessId;
        this.title = request.title();
        this.description = request.description();
        this.type = request.type();
        this.price = request.price();
        this.status = ItemStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UpdateItemRequest request, LocalDateTime now) {
        if (request.getTitle() != null) title = request.getTitle();
        if (request.hasDescription()) description = request.getDescription();
        if (request.getType() != null) type = request.getType();
        if (request.hasPrice()) price = request.getPrice();
        if (request.getStatus() != null) status = request.getStatus();
        updatedAt = now;
    }
    public Long getId() { return id; }
    public Integer getBusinessId() { return businessId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItemType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public ItemStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
