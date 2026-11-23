package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Группа риска рекомендации")
public record RecommendationGroupDTO(
        @Schema(example = "10") Integer code,
        @Schema(example = "Остановка деятельности") String name,
        @Schema(description = "Приоритет на момент расчёта", example = "1") Integer priority
) {
}
