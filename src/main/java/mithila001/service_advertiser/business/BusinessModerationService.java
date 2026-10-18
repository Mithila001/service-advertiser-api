package mithila001.service_advertiser.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import mithila001.service_advertiser.business.dto.BusinessModerationResponse;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;
import mithila001.service_advertiser.common.PaginationPolicy;
import mithila001.service_advertiser.user.User;
import mithila001.service_advertiser.user.UserRepository;
import mithila001.service_advertiser.user.UserRole;
import mithila001.service_advertiser.user.UserStatus;

@Service
@Transactional
public class BusinessModerationService {
    private final BusinessRepository businesses;
    private final BusinessModerationRepository history;
    private final UserRepository users;
    private final Clock clock;
    private final PaginationPolicy pagination;

    public BusinessModerationService(BusinessRepository businesses, BusinessModerationRepository history,
            UserRepository users, Clock clock, PaginationPolicy pagination) {
        this.businesses = businesses;
        this.history = history;
        this.users = users;
        this.clock = clock;
        this.pagination = pagination;
    }

    @Transactional(readOnly = true)
    public PageResponse<BusinessResponse> suspended(Long adminId, Integer page, Integer size) {
        activeAdmin(adminId);
        var pageable = pagination.page(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return PageResponse.from(businesses.findByStatus(BusinessStatus.SUSPENDED, pageable), BusinessResponse::from);
    }

    @Transactional(readOnly = true)
    public List<BusinessModerationResponse> history(Long adminId, Integer businessId) {
        activeAdmin(adminId);
        requireBusiness(businessId);
        return history.findHistory(businessId).stream().map(BusinessModerationResponse::from).toList();
    }

    public BusinessResponse suspend(Long adminId, Integer businessId, String reason) {
        return moderate(adminId, businessId, reason, BusinessStatus.ACTIVE,
                BusinessModerationAction.SUSPENDED);
    }

    public BusinessResponse activate(Long adminId, Integer businessId, String reason) {
        return moderate(adminId, businessId, reason, BusinessStatus.SUSPENDED,
                BusinessModerationAction.ACTIVATED);
    }

    private BusinessResponse moderate(Long adminId, Integer businessId, String reason,
            BusinessStatus expected, BusinessModerationAction action) {
        User admin = activeAdmin(adminId);
        Business business = businesses.findForUpdate(businessId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
        if (business.getStatus() != expected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Business is not " + expected.name().toLowerCase());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (action == BusinessModerationAction.SUSPENDED) business.suspend(now);
        else business.activate(now);
        history.save(new BusinessModerationEvent(business, admin, action,
                reason == null || reason.isBlank() ? null : reason.trim(), now));
        return BusinessResponse.from(business);
    }

    private User activeAdmin(Long adminId) {
        User admin = users.findById(adminId).orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (admin.getStatus() != UserStatus.ACTIVE || admin.getRole().getName() != UserRole.ADMIN) {
            throw new AccessDeniedException("Access denied");
        }
        return admin;
    }

    private Business requireBusiness(Integer businessId) {
        return businesses.findById(businessId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"));
    }
}
