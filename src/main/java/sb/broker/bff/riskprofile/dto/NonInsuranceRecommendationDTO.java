package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Нестраховая рекомендация")
public record NonInsuranceRecommendationDTO(
        @Schema(example = "801") Integer code,
        @Schema(example = "Резервная площадка") String name,
        @Schema(example = "Подготовить запасную площадку") String description,
        RecommendationGroupDTO group
) {
}
