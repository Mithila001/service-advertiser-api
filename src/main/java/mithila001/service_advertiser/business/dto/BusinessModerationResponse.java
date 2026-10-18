package mithila001.service_advertiser.business.dto;

import java.time.LocalDateTime;
import mithila001.service_advertiser.business.BusinessModerationEvent;
import mithila001.service_advertiser.business.BusinessModerationAction;

public record BusinessModerationResponse(Long id, Integer businessId, Long adminId,
        BusinessModerationAction action, String reason, LocalDateTime createdAt) {
    public static BusinessModerationResponse from(BusinessModerationEvent event) {
        return new BusinessModerationResponse(event.getId(), event.getBusiness().getId(),
                event.getAdmin().getId(), event.getAction(), event.getReason(), event.getCreatedAt());
    }
}
