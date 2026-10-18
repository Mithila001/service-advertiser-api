package mithila001.service_advertiser.category;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Categories", description = "Public predefined category catalogs. Use business and item category IDs with their corresponding resource type.")
@ApiResponses({
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @Operation(summary = "List business categories", description =
            "Returns all business categories ordered by name.")
    @GetMapping("/business")
    public List<CategoryResponse> businessCategories() { return service.businessCatalog(); }

    @Operation(summary = "List item categories", description =
            "Returns all item categories ordered by name.")
    @GetMapping("/item")
    public List<CategoryResponse> itemCategories() { return service.itemCatalog(); }
}
