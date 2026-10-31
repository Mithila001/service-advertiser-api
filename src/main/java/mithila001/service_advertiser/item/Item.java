package mithila001.service_advertiser.item;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import mithila001.service_advertiser.category.ItemCategory;
import mithila001.service_advertiser.item.dto.*;
import mithila001.service_advertiser.business.Business;

@Entity
@Table(name = "items")
public class Item {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_id", nullable = false, updatable = false)
    private Integer businessId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", insertable = false, updatable = false)
    private Business business;
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
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "item_category_assignments",
            joinColumns = @JoinColumn(name = "item_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<ItemCategory> categories = new HashSet<>();

    protected Item() {}
    public Item(Integer businessId, CreateItemRequest request, Instant now) {
        this.businessId = businessId;
        this.title = request.title();
        this.description = request.description();
        this.type = request.type();
        this.price = request.price();
        this.status = ItemStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UpdateItemRequest request, Instant now) {
        if (request.getTitle() != null) title = request.getTitle();
        if (request.hasDescription()) description = request.getDescription();
        if (request.getType() != null) type = request.getType();
        if (request.hasPrice()) price = request.getPrice();
        if (request.getStatus() != null) status = request.getStatus();
        updatedAt = now;
    }
    public void setCategories(Iterable<ItemCategory> categories) {
        this.categories.clear();
        categories.forEach(this.categories::add);
    }
    public Business getBusiness() { return business; }

    public Long getId() { return id; }
    public Integer getBusinessId() { return businessId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ItemType getType() { return type; }
    public BigDecimal getPrice() { return price; }
    public ItemStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<ItemCategory> getCategories() { return categories; }
}
