package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import sb.broker.bff.web.CalendarDate;

@Schema(description = "Документ ТС")
public record CreateAccountKaskoVehicleDocumentDTO(
        @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", message = "{error.preview-request-invalid}") String typeGuid,
        @Schema(example = "77УН123456") @NotBlank(message = "{error.preview-request-invalid}") String number,
        @Schema(example = "2024-05-01") @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String issueDate
) {
}
