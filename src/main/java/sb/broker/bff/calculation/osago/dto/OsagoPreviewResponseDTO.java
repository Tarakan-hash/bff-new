package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Предрасчёт ОСАГО (обычное и спецтехника)")
public record OsagoPreviewResponseDTO(
        @Schema(example = "calc-1") String calculationId,
        List<OsagoPreviewOfferDTO> offers
) {
}
