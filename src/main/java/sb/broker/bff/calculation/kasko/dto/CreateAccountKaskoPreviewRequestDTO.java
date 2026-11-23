package sb.broker.bff.calculation.kasko.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

/**
 * Тело предрасчёта КАСКО кабинета. Email берётся из допуска, согласия — уже в CRM: оба поля запрещены.
 */
@Schema(description = "Предрасчёт КАСКО кабинета")
public record CreateAccountKaskoPreviewRequestDTO(
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountKaskoInsurantDTO insurant,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountKaskoVehicleDTO vehicle,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountKaskoInsuranceContractDTO insuranceContract,
        @Schema(description = "Не передаётся, если предыдущего договора нет") @Valid
        CreateAccountKaskoPreviousContractDTO previousContract,
        @Schema(hidden = true) @JsonProperty("email") @Null(message = "{error.preview-request-invalid}") Object email,
        @Schema(hidden = true) @JsonProperty("consents") @Null(message = "{error.preview-request-invalid}") Object consents
) {
}
