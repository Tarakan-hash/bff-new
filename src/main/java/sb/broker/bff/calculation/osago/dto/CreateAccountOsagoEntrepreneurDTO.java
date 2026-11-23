package sb.broker.bff.calculation.osago.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import sb.broker.bff.web.CalendarDate;

@Schema(description = "Индивидуальный предприниматель")
public record CreateAccountOsagoEntrepreneurDTO(
        @NotBlank(message = "{error.preview-request-invalid}") String inn,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoContactsDTO contacts,
        @NotBlank(message = "{error.preview-request-invalid}") String shortName,
        @NotBlank(message = "{error.preview-request-invalid}") String fullName,
        @NotBlank(message = "{error.preview-request-invalid}") String surname,
        @NotBlank(message = "{error.preview-request-invalid}") String name,
        String patronymic,
        @NotNull(message = "{error.preview-request-invalid}") @CalendarDate String birthDate,
        @NotBlank(message = "{error.preview-request-invalid}") String address,
        @NotNull(message = "{error.preview-request-invalid}") @Valid CreateAccountOsagoIdentityDocumentDTO identityDocument
) {
}
