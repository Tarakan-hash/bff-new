package sb.broker.bff.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Настройки запуска workflow кабинета.
 *
 * @param defaults      значения для всех workflow
 * @param workflows     переопределения по типу workflow (простое имя интерфейса)
 * @param healthTimeout сколько ждать ответа Temporal в проверке готовности пода
 */
@Validated
@ConfigurationProperties(prefix = "bff.temporal")
public record TemporalWorkflowsProperties(
        @NotNull @Valid Defaults defaults,
        @DefaultValue Map<String, Overrides> workflows,
        @DefaultValue("2s") Duration healthTimeout
) {

    /**
     * @param taskQueue        очередь worker'а workflow-definition-service
     * @param executionTimeout предельное время выполнения workflow вместе с ретраями
     * @param awaitTimeout     сколько bff ждёт результат в рамках HTTP-запроса
     */
    public record Defaults(@NotBlank String taskQueue,
                           @NotNull Duration executionTimeout,
                           @NotNull Duration awaitTimeout) {
    }

    /**
     * Любое поле может отсутствовать — тогда берётся значение из {@link Defaults}.
     *
     * @param idPrefix префикс идентификатора workflow; по умолчанию {@code <тип>-}
     */
    public record Overrides(String taskQueue,
                            String idPrefix,
                            Duration executionTimeout,
                            Duration awaitTimeout) {
    }

    /**
     * Итоговые настройки одного workflow.
     */
    public record Settings(String taskQueue, String idPrefix, Duration executionTimeout, Duration awaitTimeout) {
    }

    public Settings settingsFor(String workflowType) {
        Optional<Overrides> overrides = Optional.ofNullable(workflows.get(workflowType));
        return new Settings(
                overrides.map(Overrides::taskQueue).orElse(defaults.taskQueue()),
                overrides.map(Overrides::idPrefix).orElse(workflowType + "-"),
                overrides.map(Overrides::executionTimeout).orElse(defaults.executionTimeout()),
                overrides.map(Overrides::awaitTimeout).orElse(defaults.awaitTimeout()));
    }
}
