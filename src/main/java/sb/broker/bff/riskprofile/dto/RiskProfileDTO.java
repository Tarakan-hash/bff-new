package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Риск-профиль — данные события {@code complete}.
 */
@Schema(description = "Риск-профиль и рекомендации (событие complete)")
public record RiskProfileDTO(
        @Schema(example = "7707083893") String inn,
        @Schema(description = "Пустая строка, если имени нет", example = "Пример") String companyName,
        @Schema(example = "62.01") String okved,
        @Schema(description = "Момент публикации снимка, ISO-8601", example = "2026-09-30T12:00:00+03:00")
        String calculatedAt,
        List<RiskGroupDTO> riskGroups,
        RecommendationsDTO recommendations
) {
}
