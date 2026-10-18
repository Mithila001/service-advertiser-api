package mithila001.service_advertiser.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import mithila001.service_advertiser.auth.dto.RegisterRequest;
import mithila001.service_advertiser.auth.dto.RegisterResponse;
import mithila001.service_advertiser.auth.dto.LoginRequest;
import mithila001.service_advertiser.auth.dto.RefreshRequest;
import mithila001.service_advertiser.auth.dto.TokenResponse;

@Tag(name = "Authentication", description = "Account registration, login, and refresh token lifecycle.")
@ApiResponses({
    @ApiResponse(responseCode = "400", ref = "#/components/responses/Error400"),
    @ApiResponse(responseCode = "500", ref = "#/components/responses/Error500")
})
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    public AuthController(RegistrationService registrationService, AuthenticationService authenticationService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
    }

    @Operation(summary = "Register an account", description =
            "Creates a CUSTOMER account. Does not log in; call login separately.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @Operation(summary = "Log in", description =
            "Returns an access JWT and an opaque refresh token. Token responses use Cache-Control: no-store.")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authenticationService.login(request));
    }

    @Operation(summary = "Refresh a session", description =
            "Use the current refresh token once. Replace both returned tokens. Session expiry is unchanged. " +
            "Reuse revokes the session; serialize refresh requests.")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authenticationService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out a session", description =
            "Revokes refresh tokens for this session. Unknown or revoked well-formed tokens also succeed. " +
            "Access JWTs remain valid until expiry.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session logged out; no response body", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
