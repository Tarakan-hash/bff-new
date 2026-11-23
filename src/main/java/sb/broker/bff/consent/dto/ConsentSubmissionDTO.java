package sb.broker.bff.consent.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Решения по согласиям: массив в корне запроса либо в поле {@code data}.
 */
@Schema(type = "object", description = "Решения по согласиям в поле data")
public record ConsentSubmissionDTO(
        @NotNull(message = "{error.invalid-consent-decisions}")
        List<@NotNull(message = "{error.invalid-consent-decisions}") @Valid ConsentDecisionDTO> data
) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ConsentSubmissionDTO {
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public static ConsentSubmissionDTO fromData(
            @JsonProperty(value = "data", required = true) List<ConsentDecisionDTO> data) {
        return new ConsentSubmissionDTO(data);
    }
}
