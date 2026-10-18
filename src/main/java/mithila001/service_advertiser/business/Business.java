package mithila001.service_advertiser.business;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import mithila001.service_advertiser.category.BusinessCategory;
import mithila001.service_advertiser.business.dto.BusinessInformation;

@Entity
@Table(name = "business")
public class Business {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "unique_name", nullable = false, unique = true, updatable = false, length = 50)
    private String uniqueName;
    @Column(name = "name", nullable = false, length = 150)
    private String name;
    @Column(name = "description", columnDefinition = "text")
    private String description;
    @Column(name = "phone", length = 20)
    private String phone;
    @Column(name = "email", length = 255)
    private String email;
    @Column(name = "website_url", length = 500)
    private String websiteUrl;
    @Column(name = "address_line_1", length = 255)
    private String addressLine1;
    @Column(name = "address_line_2", length = 255)
    private String addressLine2;
    @Column(name = "city", nullable = false, length = 100)
    private String city;
    @Column(name = "district", length = 100)
    private String district;
    @Column(name = "province", length = 100)
    private String province;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;
    @Column(name = "logo_url", length = 500)
    private String logoUrl;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BusinessStatus status;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "business_category_assignments",
            joinColumns = @JoinColumn(name = "business_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<BusinessCategory> categories = new HashSet<>();

    protected Business() {}

    public Business(String uniqueName, BusinessInformation information, LocalDateTime now) {
        this.uniqueName = uniqueName;
        this.status = BusinessStatus.ACTIVE;
        this.createdAt = now;
        update(information, now);
    }

    public void update(BusinessInformation information, LocalDateTime now) {
        this.name = information.name();
        this.description = information.description();
        this.phone = information.phone();
        this.email = information.email();
        this.websiteUrl = information.websiteUrl();
        this.addressLine1 = information.addressLine1();
        this.addressLine2 = information.addressLine2();
        this.city = information.city();
        this.district = information.district();
        this.province = information.province();
        this.postalCode = information.postalCode();
        this.latitude = information.latitude();
        this.longitude = information.longitude();
        this.logoUrl = information.logoUrl();
        this.updatedAt = now;
    }
    public void setCategories(Iterable<BusinessCategory> categories) {
        this.categories.clear();
        categories.forEach(this.categories::add);
    }
    public Integer getId() { return id; }
    public String getUniqueName() { return uniqueName; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getWebsiteUrl() { return websiteUrl; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getProvince() { return province; }
    public String getPostalCode() { return postalCode; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getLogoUrl() { return logoUrl; }
    public BusinessStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Set<BusinessCategory> getCategories() { return categories; }
}
