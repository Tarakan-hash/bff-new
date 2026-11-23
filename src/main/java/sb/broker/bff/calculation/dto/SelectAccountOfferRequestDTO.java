package sb.broker.bff.calculation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Выбор предложения")
public record SelectAccountOfferRequestDTO(
        @Schema(description = "Идентификатор workflow из ответа POST предрасчёта", example = "lk-calc-dms-…")
        @NotBlank(message = "{error.calculation-operation-id-missing}") String workflowId,
        @Schema(description = "Идентификатор выбранного предложения", example = "offer-1")
        @NotBlank(message = "{error.offer-id-missing}") String offerId
) {
}
