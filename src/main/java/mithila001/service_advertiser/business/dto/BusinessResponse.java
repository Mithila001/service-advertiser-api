package mithila001.service_advertiser.business.dto;

import java.time.Instant;
import mithila001.service_advertiser.business.*;

public record BusinessResponse(Integer id, String uniqueName, BusinessInformation information,
                               BusinessStatus status, Instant createdAt, Instant updatedAt) {
    public static BusinessResponse from(Business business) {
        return new BusinessResponse(business.getId(), business.getUniqueName(), new BusinessInformation(
                business.getName(), business.getDescription(), business.getPhone(), business.getEmail(), business.getWebsiteUrl(), business.getAddressLine1(), business.getAddressLine2(),
                business.getCityCatalogEntry() == null ? business.getCity() : business.getCityCatalogEntry().getName(),
                business.getCityCatalogEntry() == null ? business.getDistrict() : business.getCityCatalogEntry().getDistrict().getName(),
                business.getCityCatalogEntry() == null ? business.getProvince() : business.getCityCatalogEntry().getDistrict().getProvince().getName(),
                business.getPostalCode(), business.getLatitude(), business.getLongitude(), business.getLogoUrl(),
                business.getCategories().stream().map(category -> category.getId()).sorted().toList(),
                business.getCityCatalogEntry() == null ? null : business.getCityCatalogEntry().getId(),
                business.getCityCatalogEntry() == null ? null : business.getCityCatalogEntry().getDistrict().getId(),
                business.getCityCatalogEntry() == null ? null : business.getCityCatalogEntry().getDistrict().getProvince().getId()),
                business.getStatus(), business.getCreatedAt(), business.getUpdatedAt());
    }
}
