package sb.broker.bff.riskprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Рекомендации; пустые списки, если рекомендаций нет")
public record RecommendationsDTO(
        List<InsuranceRecommendationDTO> insurance,
        List<ServiceRecommendationDTO> service,
        List<NonInsuranceRecommendationDTO> nonInsurance
) {
}
