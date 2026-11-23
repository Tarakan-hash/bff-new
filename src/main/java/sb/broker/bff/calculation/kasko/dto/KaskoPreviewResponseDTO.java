package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Предрасчёт КАСКО: пустой offers, если расчёт КАСКО не запускался")
public record KaskoPreviewResponseDTO(
        @Schema(example = "calc-1") String calculationId,
        List<KaskoPreviewOfferDTO> offers
) {
}
