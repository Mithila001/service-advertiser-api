package mithila001.service_advertiser.business.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record BusinessInformation(
        @NotBlank @Size(max = 150) String name,
        String description,
        @Size(max = 20) String phone,
        @Size(max = 255) @Email String email,
        @Size(max = 500) String websiteUrl,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 100) String province,
        @Size(max = 20) String postalCode,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @Size(max = 500) String logoUrl,
        List<@NotNull @Positive Integer> categoryIds) {
    public BusinessInformation(String name, String description, String phone, String email, String websiteUrl,
            String addressLine1, String addressLine2, String city, String district, String province, String postalCode,
            BigDecimal latitude, BigDecimal longitude, String logoUrl) {
        this(name, description, phone, email, websiteUrl, addressLine1, addressLine2, city, district, province,
                postalCode, latitude, longitude, logoUrl, null);
    }
}
