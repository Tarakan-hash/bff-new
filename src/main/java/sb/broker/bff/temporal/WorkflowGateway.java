package sb.broker.bff.temporal;

import io.grpc.Context;
import jakarta.annotation.PreDestroy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowServiceException;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import io.temporal.client.WorkflowUpdateTimeoutOrCancelledException;
import io.temporal.failure.ApplicationFailure;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties.Settings;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.concurrent.TimeoutException;

/**
 * Единая точка запуска workflow кабинета и получения их результата.
 *
 * <p>Результат забирается long-poll ожиданием завершения workflow ({@link WorkflowStub#getResult}),
 * а не опросом статуса: Temporal сам держит запрос до завершения.
 *
 * <p>Идентификатор workflow: {@code <префикс><ключ->?<UUID>}. Ключ (например ИНН) позволяет
 * проверить, что пользователь запрашивает результат своего workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowGateway {

    private static final String KEY_SEPARATOR = "-";

    private static final String METRIC = "bff.workflow";

    private final WorkflowClient client;
    private final TemporalWorkflowsProperties properties;
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("temporal-update-deadline").daemon().factory());

    @PreDestroy
    void stopDeadlines() {
        deadlines.shutdownNow();
    }

    /**
     * Запускает workflow и ждёт его результат не дольше {@code await-timeout} этого workflow.
     */
    public <I, R> R execute(WorkflowContract<I, R> contract, I input) {
        String workflowId = start(contract, input, null);
        return awaitResult(contract, workflowId);
    }

    /**
     * Запускает workflow, не дожидаясь результата.
     *
     * @param businessKey ключ владельца, который войдёт в идентификатор; {@code null} — без ключа
     * @return идентификатор workflow
     */
    public <I> String start(WorkflowContract<I, ?> contract, I input, String businessKey) {
        String workflowId = settings(contract).idPrefix()
                + (businessKey == null ? "" : businessKey + KEY_SEPARATOR)
                + UUID.randomUUID();
        return start(contract, input, workflowId, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL);
    }

    /**
     * Запускает единственный выполняющийся экземпляр workflow для ключа: если workflow с этим ключом
     * уже идёт, новый не запускается (Temporal {@code USE_EXISTING}), а возвращается его идентификатор.
     *
     * @param singletonKey ключ экземпляра, например ИНН
     * @return идентификатор workflow
     */
    public <I> String startOrAttach(WorkflowContract<I, ?> contract, I input, String singletonKey) {
        String workflowId = settings(contract).idPrefix() + singletonKey;
        return start(contract, input, workflowId, WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING);
    }

    private <I> String start(WorkflowContract<I, ?> contract,
                             I input,
                             String workflowId,
                             WorkflowIdConflictPolicy conflictPolicy) {
        Settings settings = settings(contract);
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(settings.taskQueue())
                .setWorkflowExecutionTimeout(settings.executionTimeout())
                .setWorkflowIdConflictPolicy(conflictPolicy)
                .build();
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            client.newUntypedWorkflowStub(contract.workflowType(), options).start(input);
            record(sample, contract, "start", "success");
        } catch (RuntimeException e) {
            record(sample, contract, "start", "failed");
            log.error("Workflow was not started: type={}, id={}", contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow was not started: " + workflowId, e);
        }
        log.info("Workflow started: type={}, id={}, conflictPolicy={}", contract.workflowType(), workflowId, conflictPolicy);
        return workflowId;
    }

    /**
     * Ждёт результат уже запущенного workflow не дольше его {@code await-timeout}.
     */
    public <R> R awaitResult(WorkflowContract<?, R> contract, String workflowId) {
        Duration timeout = settings(contract).awaitTimeout();
        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.of(contract.workflowType()));
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failed";
        log.info("Workflow await started: type={}, id={}, timeout={}", contract.workflowType(), workflowId, timeout);
        try {
            R result = stub.getResult(timeout.toMillis(), TimeUnit.MILLISECONDS, contract.resultType());
            outcome = "success";
            log.info("Workflow completed: type={}, id={}", contract.workflowType(), workflowId);
            return result;
        } catch (TimeoutException e) {
            outcome = "timeout";
            log.warn("Workflow await timeout: type={}, id={}, timeout={}", contract.workflowType(), workflowId, timeout);
            throw new WorkflowAwaitTimeoutException(workflowId, e);
        } catch (io.temporal.client.WorkflowNotFoundException e) {
            outcome = "not_found";
            log.warn("Workflow not found: type={}, id={}", contract.workflowType(), workflowId);
            throw new WorkflowNotFoundException(workflowId, e);
        } catch (io.temporal.client.WorkflowException e) {
            log.warn("Workflow failed: type={}, id={}", contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow did not complete successfully: " + workflowId, e);
        } catch (RuntimeException e) {
            log.error("Workflow result is unavailable: type={}, id={}", contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow result is unavailable: " + workflowId, e);
        } finally {
            record(sample, contract, "await", outcome);
        }
    }

    /**
     * Запускает workflow и в том же вызове Temporal отправляет ему Update (update-with-start); ждёт результат
     * Update не дольше {@code await-timeout} workflow.
     *
     * @param businessKey ключ владельца в идентификаторе
     * @return идентификатор workflow и результат Update; результата нет — ожидание истекло, workflow идёт
     * @throws WorkflowUpdateRejectedException валидатор отклонил Update
     */
    public <I, U> StartedWithUpdate<U> startWithUpdate(WorkflowContract<I, ?> contract, I input, String businessKey,
                                                       UpdateCall<U> update) {
        Settings settings = settings(contract);
        String workflowId = settings.idPrefix() + businessKey + KEY_SEPARATOR + UUID.randomUUID();
        WorkflowStub stub = client.newUntypedWorkflowStub(contract.workflowType(), WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(settings.taskQueue())
                .setWorkflowExecutionTimeout(settings.executionTimeout())
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL)
                .build());
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Workflow start-with-update: type={}, id={}", contract.workflowType(), workflowId);
        String outcome = "failed";
        try {
            Optional<U> result = awaitUpdate(() -> stub.startUpdateWithStart(options(update),
                    new Object[]{update.argument()}, new Object[]{input}), settings.awaitTimeout());
            outcome = result.isPresent() ? "success" : "timeout";
            if (result.isPresent()) {
                log.info("Workflow start-with-update completed: type={}, id={}", contract.workflowType(), workflowId);
            } else {
                log.warn("Workflow start-with-update timeout: type={}, id={}", contract.workflowType(), workflowId);
            }
            return new StartedWithUpdate<>(workflowId, result);
        } catch (WorkflowUpdateRejectedException e) {
            outcome = "rejected";
            log.warn("Workflow start-with-update rejected: type={}, id={}, reason={}",
                    contract.workflowType(), workflowId, e.getCode());
            throw e;
        } catch (WorkflowUpdateException e) {
            outcome = "rejected";
            log.error("Workflow start-with-update update error: type={}, id={}",
                    contract.workflowType(), workflowId, e);
            throw rejected(workflowId, e);
        } catch (RuntimeException e) {
            log.error("Workflow start-with-update failed: type={}, id={}",
                    contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow was not started: " + workflowId, e);
        } finally {
            record(sample, contract, "start-update", outcome);
        }
    }

    /**
     * Update выполняющегося workflow; ждёт результат не дольше {@code timeout}.
     *
     * @return результат; пусто — ожидание истекло
     * @throws WorkflowNotFoundException       workflow нет или он уже закрыт
     * @throws WorkflowUpdateRejectedException валидатор отклонил Update или обработчик завершился ошибкой
     */
    public <U> Optional<U> update(WorkflowContract<?, ?> contract, String workflowId, UpdateCall<U> update,
                                  Duration timeout) {
        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.of(contract.workflowType()));
        Timer.Sample sample = Timer.start(meterRegistry);
        log.info("Workflow update: type={}, id={}, name={}", contract.workflowType(), workflowId, update.name());
        String outcome = "failed";
        try {
            Optional<U> result = awaitUpdate(() -> stub.startUpdate(options(update), update.argument()), timeout);
            outcome = result.isPresent() ? "success" : "timeout";
            if (result.isPresent()) {
                log.info("Workflow update completed: type={}, id={}, name={}",
                        contract.workflowType(), workflowId, update.name());
            } else {
                log.warn("Workflow update timeout: type={}, id={}, name={}, timeout={}",
                        contract.workflowType(), workflowId, update.name(), timeout);
            }
            return result;
        } catch (WorkflowUpdateRejectedException e) {
            outcome = "rejected";
            log.warn("Workflow update rejected: type={}, id={}, name={}, reason={}",
                    contract.workflowType(), workflowId, update.name(), e.getCode());
            throw e;
        } catch (WorkflowUpdateException e) {
            outcome = "rejected";
            log.error("Workflow update error: type={}, id={}, name={}",
                    contract.workflowType(), workflowId, update.name(), e);
            throw rejected(workflowId, e);
        } catch (io.temporal.client.WorkflowNotFoundException e) {
            outcome = "not_found";
            log.warn("Workflow update not found: type={}, id={}, name={}",
                    contract.workflowType(), workflowId, update.name());
            throw new WorkflowNotFoundException(workflowId, e);
        } catch (WorkflowServiceException e) {
            if (isNotFound(e)) {
                outcome = "not_found";
                log.warn("Workflow update not found: type={}, id={}, name={}",
                        contract.workflowType(), workflowId, update.name());
                throw new WorkflowNotFoundException(workflowId, e);
            }
            log.error("Update failed: type={}, id={}, name={}",
                    contract.workflowType(), workflowId, update.name(), e);
            throw new WorkflowExecutionException("Update failed: " + workflowId, e);
        } finally {
            record(sample, contract, "update", outcome);
        }
    }

    /**
     * Результат workflow, если он уже закрыт.
     *
     * @return результат; пусто — workflow ещё идёт
     * @throws WorkflowNotFoundException  workflow нет
     * @throws WorkflowExecutionException workflow завершился сбоем
     */
    public <R> Optional<R> closedResult(WorkflowContract<?, R> contract, String workflowId) {
        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId, Optional.empty(), Optional.of(contract.workflowType()));
        try {
            // статус — describe: getResult с нулевым ожиданием не отличает «идёт» от «только что закрылся»
            if (stub.describe().getStatus() == WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING) {
                log.info("Workflow is still running: type={}, id={}", contract.workflowType(), workflowId);
                return Optional.empty();
            }
            R result = stub.getResult(contract.resultType());
            log.info("Workflow closed result received: type={}, id={}", contract.workflowType(), workflowId);
            return Optional.of(result);
        } catch (io.temporal.client.WorkflowNotFoundException e) {
            log.warn("Workflow not found: type={}, id={}", contract.workflowType(), workflowId);
            throw new WorkflowNotFoundException(workflowId, e);
        } catch (WorkflowServiceException e) {
            if (isNotFound(e)) {
                log.warn("Workflow not found: type={}, id={}", contract.workflowType(), workflowId);
                throw new WorkflowNotFoundException(workflowId, e);
            }
            log.error("Workflow is unavailable: type={}, id={}", contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow is unavailable: " + workflowId, e);
        } catch (io.temporal.client.WorkflowException e) {
            log.warn("Workflow did not complete successfully: type={}, id={}", contract.workflowType(), workflowId, e);
            throw new WorkflowExecutionException("Workflow did not complete successfully: " + workflowId, e);
        }
    }

    private static <U> UpdateOptions<U> options(UpdateCall<U> update) {
        return UpdateOptions.newBuilder(update.resultType())
                .setUpdateName(update.name())
                .setWaitForStage(WorkflowUpdateStage.ACCEPTED)
                .build();
    }

    /**
     * Один deadline для принятия Update worker'ом и ожидания результата. gRPC Context ограничивает
     * также startUpdate/startUpdateWithStart и ретраи SDK, когда worker недоступен. Истечение ожидания
     * не отменяет workflow или уже принятый Update на сервере.
     */
    private <U> Optional<U> awaitUpdate(Supplier<WorkflowUpdateHandle<U>> start, Duration timeout) {
        try (Context.CancellableContext context = Context.current()
                .withDeadlineAfter(timeout.toNanos(), TimeUnit.NANOSECONDS, deadlines)) {
            Context previous = context.attach();
            try {
                WorkflowUpdateHandle<U> handle = start.get();
                long remaining = Math.max(1, context.getDeadline().timeRemaining(TimeUnit.NANOSECONDS));
                return Optional.ofNullable(handle.getResult(remaining, TimeUnit.NANOSECONDS));
            } catch (WorkflowUpdateTimeoutOrCancelledException e) {
                return Optional.empty();
            } catch (RuntimeException e) {
                if (context.getDeadline().isExpired()) {
                    return Optional.empty();
                }
                throw e;
            } finally {
                context.detach(previous);
            }
        }
    }

    private static WorkflowUpdateRejectedException rejected(String workflowId, WorkflowUpdateException e) {
        String code = e.getCause() instanceof ApplicationFailure failure ? failure.getOriginalMessage() : null;
        return new WorkflowUpdateRejectedException(workflowId, code, e);
    }

    private static boolean isNotFound(WorkflowServiceException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof StatusRuntimeException status && status.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param result результат Update; пусто — ожидание истекло
     */
    public record StartedWithUpdate<U>(String workflowId, Optional<U> result) {
    }

    /**
     * Проверяет, что workflow запущен этим контрактом для указанного ключа владельца:
     * {@link #start} ({@code <префикс><ключ>-<UUID>}) или {@link #startOrAttach} ({@code <префикс><ключ>}).
     */
    public boolean isOwnedBy(WorkflowContract<?, ?> contract, String workflowId, String businessKey) {
        String owned = settings(contract).idPrefix() + businessKey;
        return workflowId.equals(owned) || workflowId.startsWith(owned + KEY_SEPARATOR);
    }

    /**
     * {@code bff.workflow{workflow, operation=start|await|start-update|update, outcome}}: длительность запуска,
     * ожидания результата и Update.
     */
    private void record(Timer.Sample sample, WorkflowContract<?, ?> contract, String operation, String outcome) {
        sample.stop(Timer.builder(METRIC)
                .description("Запуск workflow и ожидание его результата")
                .tag("workflow", contract.workflowType())
                .tag("operation", operation)
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    private Settings settings(WorkflowContract<?, ?> contract) {
        return properties.settingsFor(contract.workflowType());
    }
}
