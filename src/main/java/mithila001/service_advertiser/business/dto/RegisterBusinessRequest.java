package mithila001.service_advertiser.business.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record RegisterBusinessRequest(
        @NotNull @Pattern(regexp = "[a-z0-9_]{3,50}") String uniqueName,
        @NotNull @Valid BusinessInformation information) {}
