package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.CalendarDate;

import java.math.BigDecimal;

@Schema(description = "Предыдущий договор ОСАГО")
public record CreateAccountOsagoPreviousContractDTO(
        @NotBlank(message = "{error.preview-request-invalid}") String insuranceCompanyName,
        @NotBlank(message = "{error.preview-request-invalid}") String contractNumber,
        @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String contractEnd,
        @NotNull(message = "{error.preview-request-invalid}") BigDecimal insurancePremium,
        BigDecimal contractFranchise
) {
}
