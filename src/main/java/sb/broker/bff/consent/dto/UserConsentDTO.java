package sb.broker.bff.consent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Строка согласия пользователя.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Согласие пользователя")
public record UserConsentDTO(
        @Schema(description = "Наименование согласия", example = "Поручение") String title,
        @Schema(description = "Тип согласия (XML_ID) из CRM", example = "PDN") String type,
        @Schema(description = "Согласие проставлено; false — в том числе на новую версию документа", example = "false")
        boolean status,
        @Schema(description = "Обязательность", example = "true") boolean required,
        @Schema(description = "Идентификатор согласия в CRM", example = "27790909-878e-45f1-a15e-2b4be833158c")
        String guid,
        @Schema(description = "Дата и время согласия, ISO-8601 с часовым поясом; нет, если CRM не прислала",
                example = "2026-10-26T12:00:00+03:00", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String date
) {
}
