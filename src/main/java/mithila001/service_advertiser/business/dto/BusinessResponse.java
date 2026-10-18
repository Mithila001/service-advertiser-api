package mithila001.service_advertiser.business.dto;

import java.time.LocalDateTime;
import java.util.List;
import mithila001.service_advertiser.business.*;

public record BusinessResponse(Integer id, String uniqueName, BusinessInformation information,
                               BusinessStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getUniqueName(), new BusinessInformation(
                business.getName(), business.getDescription(), business.getPhone(), business.getEmail(), business.getWebsiteUrl(), business.getAddressLine1(), business.getAddressLine2(), business.getCity(), business.getDistrict(), business.getProvince(), business.getPostalCode(), business.getLatitude(), business.getLongitude(), business.getLogoUrl(),
                business.getCategories().stream().map(category -> category.getId()).sorted().toList()),
                business.getStatus(), business.getCreatedAt(), business.getUpdatedAt());
    }
}
