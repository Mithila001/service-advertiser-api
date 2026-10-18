package mithila001.service_advertiser.category;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import mithila001.service_advertiser.item.Item;

@Entity
@Table(name = "item_categories")
public class ItemCategory {
    @Id
    private Integer id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false, columnDefinition = "text")
    private String description;
    @ManyToMany(mappedBy = "categories", fetch = FetchType.LAZY)
    private Set<Item> items = new HashSet<>();

    protected ItemCategory() {}

    public Integer getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
