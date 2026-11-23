package sb.broker.bff.web.openapi;

import sb.broker.bff.web.Outcome;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Бизнес-исход метода в OpenAPI; статус и текст — из {@link Outcome} и {@code messages.properties}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(OutcomeExample.List.class)
public @interface OutcomeExample {

    Outcome value();

    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        OutcomeExample[] value();
    }
}
