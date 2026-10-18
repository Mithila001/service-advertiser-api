package mithila001.service_advertiser.business.dto;

import jakarta.validation.constraints.Size;

public record BusinessModerationRequest(@Size(max = 500) String reason) {}
