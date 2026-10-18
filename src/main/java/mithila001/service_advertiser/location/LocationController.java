package mithila001.service_advertiser.location;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import mithila001.service_advertiser.common.PageResponse;

@RestController
@RequestMapping("/api/v1/locations")
public class LocationController {
    private final LocationCatalogService service;
    public LocationController(LocationCatalogService service) { this.service = service; }

    @GetMapping("/provinces")
    public List<ProvinceResponse> provinces() { return service.provinces(); }

    @GetMapping("/districts")
    public List<DistrictResponse> districts(@RequestParam(required = false) Integer provinceId) {
        return service.districts(provinceId);
    }

    @GetMapping("/cities")
    public PageResponse<CityResponse> cities(@RequestParam(required = false) String q,
            @RequestParam(required = false) Integer districtId, @RequestParam(required = false) Integer provinceId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return service.cities(q, districtId, provinceId, page, size);
    }
}
