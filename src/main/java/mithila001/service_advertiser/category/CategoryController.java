package mithila001.service_advertiser.category;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @GetMapping("/business")
    public List<CategoryResponse> businessCategories() { return service.businessCatalog(); }

    @GetMapping("/item")
    public List<CategoryResponse> itemCategories() { return service.itemCatalog(); }
}
