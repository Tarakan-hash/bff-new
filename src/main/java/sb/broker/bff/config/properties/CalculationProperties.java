package sb.broker.bff.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Предрасчёты кабинета.
 *
 * @param ownerKey         секрет HMAC владельца в идентификаторе workflow; один на все поды и ЦОД
 * @param previewTimeout   сколько GET ждёт предложений (план: «лимита 60 секунд нет»); меньше {@code bff.sse.timeout}
 * @param selectionTimeout сколько POST выбора ждёт итог; затем {@code 204}
 */
@Validated
@ConfigurationProperties(prefix = "bff.calculation")
public record CalculationProperties(@NotBlank String ownerKey,
                                    @DefaultValue("15m") @NotNull Duration previewTimeout,
                                    @DefaultValue("60s") @NotNull Duration selectionTimeout) {
}
