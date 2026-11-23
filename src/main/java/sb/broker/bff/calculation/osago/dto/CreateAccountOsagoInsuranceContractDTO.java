package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.CalendarDate;

@Schema(description = "Договор ОСАГО")
public record CreateAccountOsagoInsuranceContractDTO(
        @Schema(example = "2026-10-08") @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String startDateOsago,
        @Schema(example = "2027-10-07") @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String endDateOsago
) {
}
