package sb.broker.bff.calculation.dms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sb.broker.bff.web.StrictIntegerDeserializer;
import sb.broker.bff.web.StrictLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "Группа сотрудников")
public record CreateAccountDmsPreviewEmployeeDTO(
        @Schema(example = "10") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer count,
        @Schema(example = "1") @NotNull(message = "{error.preview-request-invalid}") @JsonDeserialize(using = StrictLongDeserializer.class) Long ageGroupId,
        @Schema(example = "7700000000000") @NotNull(message = "{error.preview-request-invalid}")
        @Size(min = 13, max = 13, message = "{error.preview-request-invalid}") String kladrId
) {
}
