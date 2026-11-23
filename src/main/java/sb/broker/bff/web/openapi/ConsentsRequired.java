package sb.broker.bff.web.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Метод закрыт обязательным согласием: без него отвечает {@code 200 {"outcome":"consents_required"}}.
 * Добавляет этот ответ в OpenAPI ({@link LkOpenApiCustomizer}).
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConsentsRequired {
}
