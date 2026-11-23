package sb.broker.bff.calculation.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Предложение ДМС")
public record DmsPreviewOfferDTO(
        @Schema(example = "offer-1") String offerId,
        @Schema(example = "success") String status,
        @Schema(example = "1") Long insuranceCompanyId,
        @Schema(example = "7700000000000") String kladrId,
        @Schema(description = "Заполнено, когда группа с этим kladrId одна", example = "10") Integer employeesCount,
        @Schema(example = "150000.50") BigDecimal totalCost,
        @Schema(example = "12") Integer lpuCount,
        @Schema(description = "Пусто при city_not_supported", example = "[\"clinic\"]") List<String> options
) {
}
