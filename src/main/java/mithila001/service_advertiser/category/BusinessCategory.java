package mithila001.service_advertiser.category;

import jakarta.persistence.*;

@Entity
@Table(name = "business_categories")
public class BusinessCategory {
    @Id
    private Integer id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String description;

    protected BusinessCategory() {}

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
