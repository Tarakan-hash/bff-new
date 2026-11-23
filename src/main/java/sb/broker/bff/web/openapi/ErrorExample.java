package sb.broker.bff.web.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ответ-ошибка метода в OpenAPI. Текст примера берётся из {@code messages.properties} по ключу —
 * тот же, что отдаёт API, поэтому документация не расходится с поведением.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ErrorExample.List.class)
public @interface ErrorExample {

    /** HTTP-статус. */
    String status();

    /** Ключ текста в {@code messages.properties}. */
    String message();

    /** Когда метод так отвечает. */
    String when();

    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        ErrorExample[] value();
    }
}
