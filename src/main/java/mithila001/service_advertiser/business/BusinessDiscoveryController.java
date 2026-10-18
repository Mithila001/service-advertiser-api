package mithila001.service_advertiser.business;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessDiscoveryController {
    private final BusinessDiscoveryService service;
    public BusinessDiscoveryController(BusinessDiscoveryService service) { this.service = service; }

    @GetMapping
    public PageResponse<BusinessResponse> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) List<Integer> categoryIds,
            @RequestParam(required = false) Integer cityId, @RequestParam(required = false) Integer districtId,
            @RequestParam(required = false) Integer provinceId, @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.search(q, categoryIds, cityId, districtId, provinceId, sort, page, size);
    }
}
