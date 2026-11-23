package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Сервисная рекомендация")
public record ServiceRecommendationDTO(
        @Schema(example = "701") Integer code,
        @Schema(example = "Аудит процессов") String name,
        @Schema(example = "Проверка непрерывности") String description
) {
}
