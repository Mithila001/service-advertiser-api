package mithila001.service_advertiser.location;

public record ProvinceResponse(Integer id, String name) {
    static ProvinceResponse from(Province province) { return new ProvinceResponse(province.getId(), province.getName()); }
}
