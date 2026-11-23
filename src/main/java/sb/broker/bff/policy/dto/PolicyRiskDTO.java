package sb.broker.bff.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Риск полиса")
public record PolicyRiskDTO(
        @Schema(example = "Ущерб") String riskName,
        @Schema(description = "Страховая сумма риска, рубли", example = "1500000.00") BigDecimal insurancePrice
) {
}
