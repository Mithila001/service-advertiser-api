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
            "Creates a CUSTOMER account. Log in separately to obtain tokens.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "409", ref = "#/components/responses/Error409")
    })
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @Operation(summary = "Log in", description =
            "Returns an access JWT and a refresh token. Do not cache token responses.")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    })
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(authenticationService.login(request));
    }

    @Operation(summary = "Refresh a session", description =
            "Use each refresh token once and save both new tokens. Session expiry is unchanged. " +
            "Reusing a token revokes the session; send one refresh request at a time.")
    @ApiResponses({
        @ApiResponse(responseCode = "401", ref = "#/components/responses/Error401")
    })
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(authenticationService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out a session", description =
            "Revokes the session's refresh tokens. Validly formatted unknown or revoked tokens also succeed. " +
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
