package mithila001.service_advertiser.business;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.business.dto.BusinessModerationRequest;
import mithila001.service_advertiser.business.dto.BusinessModerationResponse;
import mithila001.service_advertiser.business.dto.BusinessResponse;
import mithila001.service_advertiser.common.PageResponse;

@RestController
@RequestMapping("/api/v1/admin/businesses")
@PreAuthorize("hasRole('ADMIN')")
public class BusinessModerationController {
    private final BusinessModerationService service;

    public BusinessModerationController(BusinessModerationService service) { this.service = service; }

    @GetMapping("/suspended")
    public PageResponse<BusinessResponse> suspended(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return service.suspended(Long.valueOf(jwt.getSubject()), page, size);
    }

    @GetMapping("/{businessId}/history")
    public List<BusinessModerationResponse> history(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer businessId) {
        return service.history(Long.valueOf(jwt.getSubject()), businessId);
    }

    @PostMapping("/{businessId}/suspend")
    public BusinessResponse suspend(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer businessId,
            @Valid @RequestBody(required = false) BusinessModerationRequest request) {
        return service.suspend(Long.valueOf(jwt.getSubject()), businessId, request == null ? null : request.reason());
    }

    @PostMapping("/{businessId}/activate")
    public BusinessResponse activate(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer businessId,
            @Valid @RequestBody(required = false) BusinessModerationRequest request) {
        return service.activate(Long.valueOf(jwt.getSubject()), businessId, request == null ? null : request.reason());
    }
}
