package mithila001.service_advertiser.item;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.category.CategoryService;
import mithila001.service_advertiser.business.*;
import mithila001.service_advertiser.item.dto.CreateItemRequest;
import mithila001.service_advertiser.common.PaginationPolicy;

class U_ItemConfigurationTests {
    @Test
    void configuredLimitIsUsedInsteadOfDefault() {
        ItemRepository repository = mock(ItemRepository.class);
        BusinessService businesses = mock(BusinessService.class);
        Business business = mock(Business.class);
        when(businesses.providerBusiness(1L)).thenReturn(business);
        when(business.getId()).thenReturn(7);
        when(business.getStatus()).thenReturn(BusinessStatus.ACTIVE);
        when(repository.countByBusinessId(7)).thenReturn(2L);
        new ApplicationContextRunner().withUserConfiguration(ItemService.class)
                .withBean(ItemRepository.class, () -> repository)
                .withBean(BusinessService.class, () -> businesses)
                .withBean(CategoryService.class, () -> mock(CategoryService.class))
                .withBean(PaginationPolicy.class, () -> new PaginationPolicy(10, 100, 10000))
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues("app.items.max-per-business=2")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(409, assertThrows(ResponseStatusException.class,
                            () -> context.getBean(ItemService.class).create(1L,
                                    new CreateItemRequest("Item", null, ItemType.PRODUCT, null)))
                            .getStatusCode().value());
                    verify(repository, never()).save(any());
                });
    }
}
