package sb.broker.bff.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Синхронизация полисов запущена или уже идёт.
 */
@Schema(description = "Запуск принят")
public record AcceptedDTO(@Schema(description = "Всегда accepted", example = "accepted") String status) {

    public static final AcceptedDTO ACCEPTED = new AcceptedDTO("accepted");
}
