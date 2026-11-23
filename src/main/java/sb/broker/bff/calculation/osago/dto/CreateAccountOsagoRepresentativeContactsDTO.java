package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Контакты представителя")
public record CreateAccountOsagoRepresentativeContactsDTO(
        @Schema(example = "Иванов") @NotBlank(message = "{error.preview-request-invalid}") String surname,
        @Schema(example = "Иван") @NotBlank(message = "{error.preview-request-invalid}") String name,
        @Schema(example = "+74951234567") @NotBlank(message = "{error.preview-request-invalid}") String phone
) {
}
