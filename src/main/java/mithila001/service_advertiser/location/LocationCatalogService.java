package mithila001.service_advertiser.location;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.common.PaginationPolicy;
import mithila001.service_advertiser.common.SearchText;

@Service
@Transactional(readOnly = true)
public class LocationCatalogService {
    private final ProvinceRepository provinces;
    private final DistrictRepository districts;
    private final CityRepository cities;
    private final PaginationPolicy pagination;

    public LocationCatalogService(ProvinceRepository provinces, DistrictRepository districts, CityRepository cities,
            PaginationPolicy pagination) {
        this.provinces = provinces;
        this.districts = districts;
        this.cities = cities;
        this.pagination = pagination;
    }

    public List<ProvinceResponse> provinces() {
        return provinces.findAllByOrderByNameAsc().stream().map(ProvinceResponse::from).toList();
    }

    public List<DistrictResponse> districts(Integer provinceId) {
        List<District> result;
        if (provinceId == null) result = districts.findAllByOrderByNameAsc();
        else {
            require(provinces.existsById(provinceId), "Unknown province ID");
            result = districts.findAllByProvinceIdOrderByNameAsc(provinceId);
        }
        return result.stream().map(DistrictResponse::from).toList();
    }

    public PageResponse<CityResponse> cities(String query, Integer districtId, Integer provinceId, Integer page, Integer size) {
        validateSelection(null, districtId, provinceId);
        String text = query == null ? "" : query.trim();
        require(text.length() <= 150, "Search text must be at most 150 characters");
        var pageable = pagination.page(page, size, Sort.by("name").ascending().and(Sort.by("id")));
        Specification<City> specification = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!text.isEmpty()) predicates.add(cb.like(cb.lower(root.get("name")), SearchText.likePattern(text), '\\'));
            if (districtId != null) predicates.add(cb.equal(root.get("district").get("id"), districtId));
            if (provinceId != null) predicates.add(cb.equal(root.get("district").get("province").get("id"), provinceId));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<City> result = cities.findAll(specification, pageable);
        return PageResponse.from(result, CityResponse::from);
    }

    public City requireCity(Integer id) {
        return cities.findById(id).orElseThrow(() -> badRequest("Unknown city ID"));
    }

    public void validateSelection(Integer cityId, Integer districtId, Integer provinceId) {
        City city = cityId == null ? null : requireCity(cityId);
        District district = districtId == null ? null : districts.findById(districtId)
                .orElseThrow(() -> badRequest("Unknown district ID"));
        if (provinceId != null) require(provinces.existsById(provinceId), "Unknown province ID");
        if (city != null) {
            District cityDistrict = city.getDistrict();
            require(districtId == null || cityDistrict.getId().equals(districtId), "City does not belong to district");
            require(provinceId == null || cityDistrict.getProvince().getId().equals(provinceId), "City does not belong to province");
        }
        if (district != null) require(provinceId == null || district.getProvince().getId().equals(provinceId),
                "District does not belong to province");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw badRequest(message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
