package mithila001.service_advertiser.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Business information. Requires name, street addressLine1 and a catalog cityId. Optional websiteUrl/logoUrl must be absolute HTTP(S) URLs. Use unique, positive business category IDs; default maximum 3 (configurable).",
        example = "{\"name\":\"Colombo Services\",\"description\":\"Local services\",\"phone\":\"+94112345678\",\"addressLine1\":\"123 Main Street\",\"cityId\":1,\"categoryIds\":[]}")
public record BusinessInformationRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 10000) String description,
        @Size(max = 20) String phone,
        @Size(max = 255) @Email String email,
        @mithila001.service_advertiser.common.HttpUrl @Size(max = 500) String websiteUrl,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotNull @Positive Integer cityId,
        @Size(max = 20) String postalCode,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        @mithila001.service_advertiser.common.HttpUrl @Size(max = 500) String logoUrl,
        List<@NotNull @Positive Integer> categoryIds) {}
