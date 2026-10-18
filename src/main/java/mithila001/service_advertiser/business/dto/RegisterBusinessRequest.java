package mithila001.service_advertiser.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@Schema(description = "Register one business. uniqueName is immutable and uses 3 to 50 lowercase ASCII letters, digits, or underscores.",
        example = "{\"uniqueName\":\"colombo_services\",\"information\":{\"name\":\"Colombo Services\",\"cityId\":1,\"categoryIds\":[]}}")
public record RegisterBusinessRequest(
        @NotNull @Pattern(regexp = "[a-z0-9_]{3,50}") String uniqueName,
        @NotNull @Valid BusinessInformationRequest information) {}
