package mithila001.service_advertiser.business;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import mithila001.service_advertiser.business.dto.*;

@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {
    private final BusinessService service;

    public BusinessController(BusinessService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<BusinessResponse> register(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterBusinessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(Long.valueOf(jwt.getSubject()), request));
    }

    @GetMapping("/me")
    public BusinessResponse own(@AuthenticationPrincipal Jwt jwt) {
        return service.own(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping("/me")
    public BusinessResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BusinessInformationRequest request) {
        return service.update(Long.valueOf(jwt.getSubject()), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        service.delete(Long.valueOf(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{uniqueName}")
    public BusinessResponse publicProfile(@PathVariable String uniqueName) {
        return service.publicProfile(uniqueName);
    }
}
