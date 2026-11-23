package sb.broker.bff.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Тело ошибки и данные события {@code error} (ErrorEventDTO плана).
 */
@Schema(description = "Ошибка")
public record ErrorDTO(@Schema(example = "Нет доступа") String message) {
}
