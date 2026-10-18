package mithila001.service_advertiser.location;

public record CityResponse(Integer id, String name, Integer districtId, String districtName,
        Integer provinceId, String provinceName) {
    static CityResponse from(City city) {
        District district = city.getDistrict();
        Province province = district.getProvince();
        return new CityResponse(city.getId(), city.getName(), district.getId(), district.getName(), province.getId(), province.getName());
    }
}
