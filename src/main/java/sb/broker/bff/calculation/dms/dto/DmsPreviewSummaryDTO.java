package sb.broker.bff.calculation.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Сводка по страховой компании: только предложения success")
public record DmsPreviewSummaryDTO(
        Long insuranceCompanyId,
        Integer employeesCount,
        List<String> offers,
        BigDecimal totalCost,
        Integer lpuCount,
        List<String> options
) {
}
