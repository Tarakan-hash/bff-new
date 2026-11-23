package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Риск группы")
public record RiskDTO(
        @Schema(example = "101") Integer code,
        @Schema(example = "Перерыв в деятельности") String name,
        @Schema(description = "null, если значения нет", example = "Остановка ключевых процессов") String description,
        @Schema(description = "null, если значения нет", example = "Страхование имущества") String linkedProduct
) {
}
