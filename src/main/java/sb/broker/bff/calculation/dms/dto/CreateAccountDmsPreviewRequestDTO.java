package sb.broker.bff.calculation.dms.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * Тело предрасчёта ДМС кабинета. Email и ИНН берётся из допуска, согласия — уже в CRM: оба поля в теле запрещены.
 */
@Schema(description = "Предрасчёт ДМС кабинета")
public record CreateAccountDmsPreviewRequestDTO(
        @Schema(example = "company") @NotNull(message = "{error.preview-request-invalid}")
        @Pattern(regexp = "^(person|company|entrepreneur|self_employed)$", message = "{error.preview-request-invalid}")
        String companyType,
        @Schema(hidden = true) @JsonProperty("inn") @Null(message = "{error.preview-request-invalid}") String inn,
        @Schema(description = "Код региона; по нему ищется ГОСБ", example = "77")
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer regionCode,
        @Schema(example = "Иванов") @NotBlank(message = "{error.preview-request-invalid}") String surname,
        @Schema(example = "Иван") @NotBlank(message = "{error.preview-request-invalid}") String name,
        @Schema(example = "+74951234567") @NotBlank(message = "{error.preview-request-invalid}") String phone,
        @NotEmpty(message = "{error.preview-request-invalid}")
        List<@Valid @NotNull(message = "{error.preview-request-invalid}") CreateAccountDmsPreviewEmployeeDTO> employees,
        @Schema(example = "dms") @NotNull(message = "{error.preview-request-invalid}")
        @Pattern(regexp = "^dms$", message = "{error.preview-request-invalid}") String product,
        @Schema(hidden = true) @JsonProperty("email") @Null(message = "{error.preview-request-invalid}") Object email,
        @Schema(hidden = true) @JsonProperty("consents") @Null(message = "{error.preview-request-invalid}") Object consents,
        @Schema(description = "ГОСБ; отсутствие или пустая строка — определить по региону",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "38") String gosb
) {
}
