package sb.broker.bff.calculation.osago.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import java.util.Objects;
import java.util.stream.Stream;

@Schema(description = "Собственник: ровно одна ветка")
public record CreateAccountOsagoOwnerDTO(
        @Valid CreateAccountOsagoCompanyDTO company,
        @Valid CreateAccountOsagoEntrepreneurDTO entrepreneur,
        @Valid CreateAccountOsagoPersonDTO person
) {

    /** Ровно одна ветка. */
    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "{error.preview-request-invalid}")
    public boolean isExactlyOneBranch() {
        return Stream.of(company, entrepreneur, person).filter(Objects::nonNull).count() == 1;
    }
}
