package mithila001.service_advertiser.category;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import mithila001.service_advertiser.business.Business;

@Entity
@Table(name = "business_categories")
public class BusinessCategory {
    @Id
    private Integer id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    private Set<Business> businesses = new HashSet<>();

    protected BusinessCategory() {}

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
