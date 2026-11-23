package sb.broker.bff.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.workflow.api.consent.ConsentType;

/**
 * Решение пользователя по одному согласию.
 */
@Schema(description = "Решение по согласию")
public record ConsentDecisionDTO(
        @Schema(description = "Тип согласия; один тип — один элемент", example = "PDN")
        @NotNull(message = "{error.invalid-consent-decisions}") ConsentType type,
        @Schema(description = "Пользователь отметил согласие; для PDN допустимо только true", example = "true")
        @NotNull(message = "{error.invalid-consent-decisions}") Boolean consent
) {
}
