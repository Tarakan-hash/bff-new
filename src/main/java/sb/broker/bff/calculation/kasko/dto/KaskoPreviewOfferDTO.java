package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Предложение КАСКО")
public record KaskoPreviewOfferDTO(
        @Schema(example = "offer-1") String offerId,
        @Schema(example = "success") String status,
        @Schema(description = "Только при error") String errorMessage,
        @Schema(example = "7") Long insuranceCompanyId,
        @Schema(example = "84500.50") BigDecimal totalCost
) {
}
