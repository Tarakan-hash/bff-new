package sb.broker.bff.calculation.osago.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

import java.util.List;

@Schema(description = "Предрасчёт ОСАГО спецтехники кабинета; email и согласия в теле запрещены")
public record CreateAccountOsagoSpecialVehicleRequestDTO(
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoRepresentativeDTO representative,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoInsurantDTO insurant,
        @NotEmpty(message = "{error.preview-request-invalid}") List<@Valid @NotNull(message = "{error.preview-request-invalid}") CreateAccountOsagoSpecialVehicleDTO> vehicles,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoInsuranceContractDTO insuranceContract,
        @Schema(hidden = true) @JsonProperty("email") @Null(message = "{error.preview-request-invalid}") Object email,
        @Schema(hidden = true) @JsonProperty("consents") @Null(message = "{error.preview-request-invalid}") Object consents
) {
}
