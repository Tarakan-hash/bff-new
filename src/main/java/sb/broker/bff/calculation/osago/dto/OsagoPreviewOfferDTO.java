package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Предложение ОСАГО")
public record OsagoPreviewOfferDTO(
        @Schema(example = "offer-1") String offerId,
        @Schema(example = "success") String status,
        @Schema(description = "Только при error") String errorMessage,
        Long insuranceCompanyId,
        BigDecimal totalCost
) {
}
