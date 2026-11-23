package sb.broker.bff.logging;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.ConsoleAppender;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLoggingConfigurationTest {

    @Test
    void logback_shouldWriteRequiredJsonFieldsAndException() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("HOSTNAME", "bff-lk-pod");
        context.putProperty("LOGGING_TIME_ZONE", "Europe/Moscow");
        context.putProperty("appName", "bff_lk service");

        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            try (InputStream xml = Objects.requireNonNull(getClass().getResourceAsStream("/logback-spring.xml"))) {
                configurator.doConfigure(xml);
            }

            ConsoleAppender<ILoggingEvent> appender = Objects.requireNonNull((ConsoleAppender<ILoggingEvent>)
                    context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("JSON_CONSOLE"));
            JsonMapper json = JsonMapper.builder().build();

            LoggingEvent event = new LoggingEvent();
            event.setLevel(Level.INFO);
            event.setMessage("Ответ \"готов\"\nследующая строка");
            event.setInstant(Instant.parse("2025-11-20T08:52:01.987Z"));

            String line = new String(appender.getEncoder().encode(event), StandardCharsets.UTF_8);
            var fields = json.readTree(line);

            assertThat(line).endsWith("\n");
            assertThat(line.lines().count()).isEqualTo(1);
            assertThat(fields.size()).isEqualTo(6);
            assertThat(fields.path("level").stringValue()).isEqualTo("info");
            assertThat(fields.path("message").stringValue()).isEqualTo("Ответ \"готов\"\nследующая строка");
            assertThat(fields.path("server-name").stringValue()).isEqualTo("bff-lk-pod");
            assertThat(fields.path("service-name").stringValue()).isEqualTo("bff-lk-service");
            assertThat(fields.path("time-generating").stringValue()).isEqualTo("2025-11-20T11:52:01+03:00");

            for (Level level : List.of(Level.TRACE, Level.DEBUG, Level.WARN)) {
                LoggingEvent levelEvent = new LoggingEvent();
                levelEvent.setLevel(level);
                levelEvent.setMessage("Проверка уровня");
                levelEvent.setInstant(Instant.parse("2025-11-20T08:52:01Z"));
                String levelLine = new String(appender.getEncoder().encode(levelEvent), StandardCharsets.UTF_8);
                assertThat(json.readTree(levelLine).path("level").stringValue())
                        .isEqualTo(level.toString().toLowerCase(Locale.ROOT));
            }

            LoggingEvent failed = new LoggingEvent();
            failed.setLevel(Level.ERROR);
            failed.setMessage("Запись не выполнена");
            failed.setInstant(Instant.parse("2025-11-20T08:52:01Z"));
            failed.setThrowableProxy(new ThrowableProxy(new IllegalStateException("write failed")));

            String failedLine = new String(appender.getEncoder().encode(failed), StandardCharsets.UTF_8);
            var failedFields = json.readTree(failedLine);

            assertThat(failedLine.lines().count()).isEqualTo(1);
            assertThat(failedFields.size()).isEqualTo(6);
            assertThat(failedFields.path("level").stringValue()).isEqualTo("error");
            assertThat(failedFields.path("message").stringValue())
                    .startsWith("Запись не выполнена\njava.lang.IllegalStateException: write failed");
        } finally {
            context.stop();
        }
    }
}

