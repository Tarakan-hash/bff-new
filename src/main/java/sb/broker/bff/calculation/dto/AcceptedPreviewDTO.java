package sb.broker.bff.calculation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Предрасчёт принят")
public record AcceptedPreviewDTO(
        @Schema(description = "Идентификатор workflow предрасчёта для GET и выбора предложения",
                example = "lk-calc-dms-3f2a9c0d1e4b5a6978c0d1e2f3a4b5c6-0f8e7d6c-5b4a-3928-1706-f5e4d3c2b1a0")
        String workflowId
) {
}
