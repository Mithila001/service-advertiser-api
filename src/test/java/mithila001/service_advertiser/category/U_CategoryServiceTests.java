package mithila001.service_advertiser.category;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class U_CategoryServiceTests {
    private final BusinessCategoryRepository businesses = mock(BusinessCategoryRepository.class);
    private final ItemCategoryRepository items = mock(ItemCategoryRepository.class);

    @Test
    void usesConfiguredSelectionLimitsAndRequiresPositiveLimits() {
        CategoryService service = new CategoryService(businesses, items, 1, 1);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.businessSelection(List.of(1, 2))).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.itemSelection(List.of(1, 2))).getStatusCode().value());
        assertThrows(IllegalArgumentException.class, () -> new CategoryService(businesses, items, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new CategoryService(businesses, items, 3, 0));
    }
}
