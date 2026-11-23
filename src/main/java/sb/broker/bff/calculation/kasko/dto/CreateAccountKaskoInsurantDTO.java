package sb.broker.bff.calculation.kasko.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "Страхователь КАСКО")
public record CreateAccountKaskoInsurantDTO(
        @Schema(example = "company") @NotNull(message = "{error.preview-request-invalid}")
        @Pattern(regexp = "^(person|company|entrepreneur|self_employed)$", message = "{error.preview-request-invalid}") String companyType,
        @Schema(example = "7707083893") @NotBlank(message = "{error.preview-request-invalid}") String inn,
        @Schema(description = "Код региона; по нему ищется ГОСБ", example = "77") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer regionCode,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountKaskoContactsDTO contacts,
        @Schema(example = "kasko") @NotNull(message = "{error.preview-request-invalid}") @Pattern(regexp = "^kasko$", message = "{error.preview-request-invalid}") String product,
        @Schema(description = "ГОСБ; отсутствие или пустая строка — определить по региону",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, example = "38") String gosb
) {
    public CreateAccountKaskoInsurantDTO(String companyType, String inn, Integer regionCode,
            CreateAccountKaskoContactsDTO contacts, String product) {
        this(companyType, inn, regionCode, contacts, product, null);
    }
}
