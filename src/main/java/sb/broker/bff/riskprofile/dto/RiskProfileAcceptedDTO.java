package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Запущен расчёт риск-профиля (или идёт расчёт этого ИНН)")
public record RiskProfileAcceptedDTO(
        @Schema(description = "Идентификатор сессии для GET", example = "lk-risk-profile-7707083893") String workflowId
) {
}
