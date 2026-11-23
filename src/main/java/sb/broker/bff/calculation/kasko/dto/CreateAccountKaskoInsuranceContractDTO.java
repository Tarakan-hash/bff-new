package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.CalendarDate;

import java.math.BigDecimal;

@Schema(description = "Договор КАСКО")
public record CreateAccountKaskoInsuranceContractDTO(
        @Schema(example = "2026-10-08") @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String startDate,
        @Schema(description = "Без поля — год со дня начала минус день", example = "2027-10-07")
        @CalendarDate String endDate,
        @Schema(example = "1500000") @NotNull(message = "{error.preview-request-invalid}") BigDecimal insuranceCost
) {
}
