package mithila001.service_advertiser.common;

import java.lang.annotation.*;
import java.net.URI;
import jakarta.validation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrl.Validator.class)
public @interface HttpUrl {
    String message() default "Must be an absolute HTTP(S) URL with a host";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<HttpUrl, String> {
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            try {
                URI uri = URI.create(value);
                return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        && uri.getHost() != null && !uri.getHost().isBlank();
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }
}
