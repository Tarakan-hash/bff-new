package sb.broker.bff.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Кэши пода bff. {@code ttl = 0} выключает кэш.
 *
 * @param access  выданный допуск (email + политика согласий): повторные запросы сессии не запускают
 *                CheckLkAccessWorkflow. Отказы не кэшируются
 * @param catalog ответы справочников: не зависят от пользователя
 */
@Validated
@ConfigurationProperties(prefix = "bff.cache")
public record CacheProperties(@DefaultValue @Valid Spec access, @DefaultValue @Valid Spec catalog) {

    public record Spec(@DefaultValue("0s") @NotNull Duration ttl, @DefaultValue("10000") @Positive long maxSize) {

        public boolean enabled() {
            return !ttl.isZero();
        }
    }
}
