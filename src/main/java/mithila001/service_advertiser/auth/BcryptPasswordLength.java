package mithila001.service_advertiser.auth;
import java.lang.annotation.*;
import java.nio.charset.StandardCharsets;
import jakarta.validation.*;
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptPasswordLength.Validator.class)
public @interface BcryptPasswordLength {
    String message() default "Must be at most 72 UTF-8 bytes";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    class Validator implements ConstraintValidator<BcryptPasswordLength, String> {
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }
}
