package sb.broker.bff.web;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** Календарная дата YYYY-MM-DD; обязательность задаётся отдельно через NotNull. */
@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = CalendarDateValidator.class)
public @interface CalendarDate {
    String message() default "{error.preview-request-invalid}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
