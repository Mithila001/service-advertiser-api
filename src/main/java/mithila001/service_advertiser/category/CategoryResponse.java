package mithila001.service_advertiser.category;

public record CategoryResponse(Integer id, String name, String description) {
    static CategoryResponse from(BusinessCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }

    static CategoryResponse from(ItemCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription());
    }
}
