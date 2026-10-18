package mithila001.service_advertiser.category;

import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final BusinessCategoryRepository businessCategories;
    private final ItemCategoryRepository itemCategories;
    private final int maxPerBusiness;
    private final int maxPerItem;

    public CategoryService(BusinessCategoryRepository businessCategories, ItemCategoryRepository itemCategories,
            @Value("${app.categories.max-per-business:3}") int maxPerBusiness,
            @Value("${app.categories.max-per-item:2}") int maxPerItem) {
        if (maxPerBusiness < 1 || maxPerItem < 1) throw new IllegalArgumentException("Category limits must be positive");
        this.businessCategories = businessCategories;
        this.itemCategories = itemCategories;
        this.maxPerBusiness = maxPerBusiness;
        this.maxPerItem = maxPerItem;
    }

    public List<CategoryResponse> businessCatalog() {
        return businessCategories.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    public List<CategoryResponse> itemCatalog() {
        return itemCategories.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    public List<BusinessCategory> businessSelection(List<Integer> ids) {
        return select(ids, maxPerBusiness, businessCategories, "business");
    }

    public List<ItemCategory> itemSelection(List<Integer> ids) {
        return select(ids, maxPerItem, itemCategories, "item");
    }

    private static <T> List<T> select(List<Integer> ids, int max, org.springframework.data.jpa.repository.JpaRepository<T, Integer> repository,
            String kind) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > max) throw invalid("Too many " + kind + " categories");
        if (ids.stream().anyMatch(id -> id == null || id < 1) || new HashSet<>(ids).size() != ids.size()) {
            throw invalid("Category IDs must be positive and unique");
        }
        List<T> categories = repository.findAllById(ids).stream().toList();
        if (categories.size() != ids.size()) throw invalid("Unknown " + kind + " category ID");
        return categories;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
