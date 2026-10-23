package mithila001.service_advertiser.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import mithila001.service_advertiser.auth.InvalidRefreshTokenException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> {
            if (!fieldErrors.containsKey(error.getField()) || "NotBlank".equals(error.getCode())) {
                fieldErrors.put(error.getField(), error.getDefaultMessage());
            }
        });

        return response(HttpStatus.BAD_REQUEST, "Validation Failed", "Invalid request data", fieldErrors);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException exception) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid request parameter", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException exception) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed JSON request", Map.of());
    }

    @ExceptionHandler({ BadCredentialsException.class, InvalidRefreshTokenException.class })
    public ResponseEntity<ErrorResponse> handleAuthentication(RuntimeException exception) {
        if (exception instanceof InvalidRefreshTokenException) {
            log.atWarn().addKeyValue("event", "refresh_rejected")
                    .addKeyValue("reason", "invalid_refresh_token").log("Refresh rejected");
        }
        return response(HttpStatus.UNAUTHORIZED, "Unauthorized", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return response(HttpStatus.FORBIDDEN, "Forbidden", "Access denied", Map.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(DuplicateEmailException exception) {
        return response(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), Map.of());
    }

    @ExceptionHandler(RoleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingRole(RoleNotFoundException exception) {
        log.atError().addKeyValue("event", "missing_registration_role")
                .setCause(exception).log("Registration role is missing");
        return internalError();
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleStatus(org.springframework.web.server.ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, status.getReasonPhrase(), exception.getReason(), Map.of());
    }

    @ExceptionHandler(mithila001.service_advertiser.config.RateLimiter.Rejected.class)
    public ResponseEntity<ErrorResponse> handleRate(mithila001.service_advertiser.config.RateLimiter.Rejected e) {
        log.atWarn().addKeyValue("event", e.status == 429 ? "rate_limit_rejected" : "rate_limit_capacity_rejected").addKeyValue("policy", e.policy).addKeyValue("reason", e.status == 429 ? "budget_exhausted" : "storage_full").log("Request admission rejected");
        return ResponseEntity.status(e.status).header("Retry-After", Long.toString(e.retry)).body(new ErrorResponse(e.status,
                e.status == 429 ? "Too Many Requests" : "Service Unavailable", e.status == 429 ? "Too many requests. Please try again later." : "Service is temporarily unavailable", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String reason = cause instanceof java.sql.SQLTransientConnectionException ? "acquisition"
                    : cause instanceof org.springframework.dao.QueryTimeoutException || cause instanceof org.hibernate.QueryTimeoutException ? "statement" : null;
            if (reason != null) {
                log.atWarn().addKeyValue("event", "database_timeout").addKeyValue("reason", reason).log("Database operation timed out");
                return response(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", "Service is temporarily unavailable", Map.of());
            }
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && "business_unique_name_key".equals(violation.getConstraintName())) {
                return response(HttpStatus.CONFLICT, "Conflict", "Business unique name is already registered", Map.of());
            }
        }
        if (hasEmailUniqueConstraint(exception)) {
            return response(HttpStatus.CONFLICT, "Conflict", "Email is already registered", Map.of());
        }

        log.atError().addKeyValue("event", "unexpected_error")
                .setCause(exception).log("Unexpected API error");
        return internalError();
    }

    private static boolean hasEmailUniqueConstraint(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ErrorResponse> internalError() {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Service is temporarily unavailable", Map.of());
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String error, String message, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), error, message, fieldErrors));
    }

    public record ErrorResponse(int status, String error, String message, Map<String, String> fieldErrors) {
    }
}
