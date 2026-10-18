package mithila001.service_advertiser.location;

public record DistrictResponse(Integer id, String name, Integer provinceId, String provinceName) {
    static DistrictResponse from(District district) {
        return new DistrictResponse(district.getId(), district.getName(), district.getProvince().getId(), district.getProvince().getName());
    }
}
