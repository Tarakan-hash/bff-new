package sb.broker.bff.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Полис — элемент данных события {@code complete}. Поля не пересчитываются.
 */
@Schema(description = "Полис (событие complete — массив)")
public record PolicyDTO(
        @Schema(example = "транспорт") String insuranceGroup,
        @Schema(example = "КАСКО-2026/014") String policyNumber,
        @Schema(example = "СК Пример") String insuranceCompany,
        @Schema(description = "Общая премия, рубли", example = "84500.00") BigDecimal totalInsurancePremium,
        @Schema(example = "2026-01-15T00:00:00+03:00") String validFrom,
        @Schema(example = "2027-01-14T00:00:00+03:00") String validTo,
        @Schema(example = "2026-01-10T10:30:00+03:00") String issueDate,
        @Schema(description = "Дней до окончания (Europe/Moscow); считает Policy Profile", example = "112")
        Long daysToExpire,
        @Schema(description = "Идентификатор продукта каталога", example = "10") Long productId,
        @Schema(example = "автокаско") String insuranceType,
        @Schema(example = "новый") String dealType,
        @Schema(example = "4") Integer paymentCount,
        @Schema(description = "Страховая сумма, рубли", example = "1500000.00") BigDecimal insuredAmount,
        List<PolicyRiskDTO> risks
) {
}
