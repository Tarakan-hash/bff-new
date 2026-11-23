package sb.broker.bff.web.openapi;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.dto.OutcomeDTO;

/**
 * OpenAPI кабинета: bearer JWT; у каждого метода {@code 401} и {@code 500}; ответы из {@link ErrorExample},
 * {@link OutcomeExample} и {@link ConsentsRequired}. Тексты примеров — из {@code messages.properties},
 * то есть те же, что отдаёт API.
 */
@Configuration
public class LkOpenApiCustomizer {

    private static final String BEARER = "bearerJwt";
    private static final String JSON = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

    private final Messages messages;

    public LkOpenApiCustomizer(Messages messages) {
        this.messages = messages;
    }

    @Bean
    public OpenAPI lkOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER, new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .bearerFormat("JWT").description("Токен Keycloak (realm lk)"));
        ModelConverters.getInstance().readAll(ErrorDTO.class).forEach(components::addSchemas);
        ModelConverters.getInstance().readAll(OutcomeDTO.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info().title("bff-lk").version("v1").description("BFF личного кабинета"))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public OperationCustomizer lkResponses() {
        return (operation, handlerMethod) -> {
            AnnotatedElementUtils.findMergedRepeatableAnnotations(handlerMethod.getMethod(), ErrorExample.class)
                    .forEach(error -> example(operation, error.status(), error.when(), ErrorDTO.class,
                            new ErrorDTO(messages.get(error.message()))));
            AnnotatedElementUtils.findMergedRepeatableAnnotations(handlerMethod.getMethod(), OutcomeExample.class)
                    .forEach(outcome -> example(operation, String.valueOf(outcome.value().getStatus().value()),
                            outcome.value().getCode(), OutcomeDTO.class, messages.outcome(outcome.value())));
            if (handlerMethod.hasMethodAnnotation(ConsentsRequired.class)) {
                example(operation, "200", "Нет обязательного согласия; действие не выполнено", OutcomeDTO.class,
                        messages.outcome(Outcome.CONSENTS_REQUIRED));
            }
            example(operation, "401", "Нет заголовка Authorization, токен недействителен или email не пользователя кабинета",
                    ErrorDTO.class, messages.error(ErrorType.UNAUTHORIZED));
            example(operation, "500", "Сбой workflow или сервиса", ErrorDTO.class, messages.error(ErrorType.INTERNAL_ERROR));
            return operation;
        };
    }

    /**
     * Добавляет JSON-пример к ответу с этим статусом (создаёт ответ, если его нет). Описание ответа
     * дополняется условием.
     */
    private static void example(Operation operation, String status, String when, Class<?> schema, Object value) {
        ApiResponse response = operation.getResponses().computeIfAbsent(status, ignored -> new ApiResponse());
        response.description(response.getDescription() == null || response.getDescription().isBlank()
                ? when
                : response.getDescription() + "; " + when);
        if (response.getContent() == null) {
            response.content(new Content());
        }
        MediaType json = response.getContent().computeIfAbsent(JSON, ignored -> new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + schema.getSimpleName())));
        json.addExamples(status + "-" + (json.getExamples() == null ? 0 : json.getExamples().size()),
                new Example().summary(when).value(value));
    }
}
