package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "Представитель; почта — из допуска")
public record CreateAccountOsagoRepresentativeDTO(
        @Schema(description = "Код региона; по нему ищется ГОСБ", example = "77") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer regionCode,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoRepresentativeContactsDTO contacts
) {
}
