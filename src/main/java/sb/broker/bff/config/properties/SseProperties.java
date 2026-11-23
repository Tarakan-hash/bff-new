package sb.broker.bff.config.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param timeout   предельное время жизни SSE-потока; должно превышать ожидание результата
 * @param heartbeat период комментария-heartbeat в молчащем потоке; меньше read timeout ingress, {@code 0} — выключен
 */
@Validated
@ConfigurationProperties(prefix = "bff.sse")
public record SseProperties(@DefaultValue("10m") @NotNull Duration timeout,
                            @DefaultValue("15s") @NotNull Duration heartbeat) {
}
