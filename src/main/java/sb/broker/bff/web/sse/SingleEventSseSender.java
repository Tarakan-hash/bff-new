package sb.broker.bff.web.sse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sb.broker.bff.config.properties.SseProperties;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * SSE-поток из одного события: поток открывается сразу, событие вычисляется в фоне,
 * после отправки поток закрывается.
 *
 * <p>Пока событие считается (до {@code await-timeout} workflow, минуты), в поток раз в {@code bff.sse.heartbeat}
 * пишется комментарий SSE: иначе ingress и балансировщики рвут молчащее соединение по read timeout
 * (у ingress-nginx — 60 секунд). EventSource комментарии пропускает.
 */
@Slf4j
@Component
public class SingleEventSseSender {

    private static final String HEARTBEAT = "keep-alive";

    private final AsyncTaskExecutor executor;
    private final SseProperties properties;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("sse-heartbeat").daemon().factory());

    public SingleEventSseSender(
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) AsyncTaskExecutor executor,
            SseProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    /**
     * @param event    вычисляет событие; может блокироваться (ожидание workflow, чтение upstream)
     * @param fallback событие, если вычисление или отправка не удались
     */
    public SseEmitter send(Supplier<SseEvent> event, Supplier<SseEvent> fallback) {
        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        // поток пережил все ожидания: закрываем его, не отдавая ошибку в уже открытый text/event-stream
        emitter.onTimeout(emitter::complete);
        Future<?> heartbeat = startHeartbeat(emitter);
        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onError(e -> heartbeat.cancel(false));
        // событие считается в другом потоке, а MDC привязан к потоку: контекст запроса передаётся явно,
        // иначе trace-id есть только у строк контроллера
        Map<String, String> context = MDC.getCopyOfContextMap();
        executor.execute(() -> {
            if (context != null) {
                MDC.setContextMap(context);
            }
            try {
                emit(emitter, event.get());
            } catch (AsyncRequestNotUsableException e) {
                log.error("Client disconnected before SSE event could be delivered");
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE event was not delivered, sending fallback", e);
                emitFallback(emitter, fallback);
            } finally {
                MDC.clear();
                heartbeat.cancel(false);
            }
        });
        return emitter;
    }

    @PreDestroy
    void stopHeartbeats() {
        heartbeats.shutdownNow();
    }

    /**
     * Комментарий в поток раз в {@code heartbeat}. Ошибка записи (клиент ушёл) останавливает только heartbeat —
     * исключение из периодической задачи отменяет её следующие запуски; фоновое вычисление события завершится само.
     */
    private Future<?> startHeartbeat(SseEmitter emitter) {
        long period = properties.heartbeat().toMillis();
        if (period <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return heartbeats.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment(HEARTBEAT));
            } catch (IOException | IllegalStateException e) {
                log.debug("SSE heartbeat was not delivered, client is gone: {}", e.toString());
                throw new IllegalStateException("SSE heartbeat stopped", e);
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private static void emitFallback(SseEmitter emitter, Supplier<SseEvent> fallback) {
        try {
            emit(emitter, fallback.get());
        } catch (Exception e) {
            log.warn("SSE fallback event was not delivered", e);
            emitter.completeWithError(e);
        }
    }

    private static void emit(SseEmitter emitter, SseEvent event) throws IOException {
        emitter.send(SseEmitter.event().name(event.name()).data(event.data(), MediaType.APPLICATION_JSON));
        emitter.complete();
    }
}
