package sb.broker.bff.calculation.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Предрасчёт ДМС: страница предложений и сводки по всему отобранному набору")
public record DmsPreviewResponseDTO(
        @Schema(example = "calc-1") String calculationId,
        List<DmsPreviewOfferDTO> offers,
        List<DmsPreviewSummaryCityDTO> summaryCities,
        List<DmsPreviewSummaryDTO> summary
) {
}
