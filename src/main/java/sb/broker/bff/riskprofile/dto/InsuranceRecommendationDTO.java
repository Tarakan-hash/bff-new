package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Страховая рекомендация")
public record InsuranceRecommendationDTO(
        @Schema(example = "501") Integer code,
        @Schema(example = "Страхование перерыва") String name,
        RecommendationGroupDTO group
) {
}
