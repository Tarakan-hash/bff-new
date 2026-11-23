package sb.broker.bff.calculation.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Сводка по страховой компании и городу")
public record DmsPreviewSummaryCityDTO(
        Long insuranceCompanyId,
        Integer employeesCount,
        String kladrId,
        List<String> offers,
        BigDecimal totalCost,
        Integer lpuCount,
        List<String> options
) {
}
