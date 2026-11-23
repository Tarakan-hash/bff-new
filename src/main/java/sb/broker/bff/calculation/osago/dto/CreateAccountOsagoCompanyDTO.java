package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.StrictIntegerDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "Юридическое лицо")
public record CreateAccountOsagoCompanyDTO(
        @NotBlank(message = "{error.preview-request-invalid}") String inn,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoContactsDTO contacts,
        @NotBlank(message = "{error.preview-request-invalid}") String shortName,
        @NotBlank(message = "{error.preview-request-invalid}") String fullName,
        @NotBlank(message = "{error.preview-request-invalid}") String ogrn,
        @NotBlank(message = "{error.preview-request-invalid}") String kpp,
        @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer opf,
        @NotBlank(message = "{error.preview-request-invalid}") String address,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoIdentityDocumentDTO identityDocument
) {
}
