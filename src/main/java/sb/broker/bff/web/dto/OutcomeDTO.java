package sb.broker.bff.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Бизнес-исход: {@code consents_required}, {@code accepted}, {@code region_not_found}, {@code gosb_not_found}, {@code product_not_found}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Бизнес-исход")
public record OutcomeDTO(
        @Schema(example = "consents_required") String outcome,
        @Schema(example = "Требуется согласие", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String message
) {
}
