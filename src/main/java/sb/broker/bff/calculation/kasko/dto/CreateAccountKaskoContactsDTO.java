package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Контакты страхователя; почта — из допуска")
public record CreateAccountKaskoContactsDTO(
        @Schema(example = "Иванов") @NotBlank(message = "{error.preview-request-invalid}") String surname,
        @Schema(example = "Иван") @NotBlank(message = "{error.preview-request-invalid}") String name,
        @Schema(example = "+74951234567") @NotBlank(message = "{error.preview-request-invalid}") String phone
) {
}
