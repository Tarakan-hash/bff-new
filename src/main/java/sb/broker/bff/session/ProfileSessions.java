package sb.broker.bff.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sb.broker.bff.access.ConsentPolicy;
import sb.broker.bff.access.LkAccessService;
import sb.broker.bff.access.LkUser;
import sb.broker.bff.temporal.WorkflowContract;
import sb.broker.bff.temporal.WorkflowExecutionException;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.temporal.WorkflowNotFoundException;
import sb.broker.bff.web.sse.SingleEventSseSender;
import sb.broker.bff.web.sse.SseEvent;

import java.util.function.Function;

/**
 * Асинхронные сессии профилей организации: POST запускает workflow, GET открывает SSE
 * и отдаёт одно событие по его результату.
 *
 * <p>Сессия привязана к ИНН: идентификатор workflow содержит ИНН, и GET чужой сессии
 * отвечает так же, как GET несуществующей.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileSessions {

    private final LkAccessService access;
    private final WorkflowGateway workflows;
    private final SingleEventSseSender sse;

    /**
     * Новая сессия на каждый запрос.
     *
     * @param input вход workflow по пользователю, получившему допуск
     * @return идентификатор workflow для следующего GET
     */
    public <I> String start(WorkflowContract<I, ?> contract, Function<LkUser, I> input) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        String workflowId = workflows.start(contract, input.apply(user), user.inn());
        log.info("Session started: workflow={}, id={}", contract.workflowType(), workflowId);
        return workflowId;
    }

    /**
     * Одна выполняющаяся сессия на ИНН: повторный запрос, пока сессия идёт, получает её идентификатор.
     */
    public <I> String startOrAttach(WorkflowContract<I, ?> contract, Function<LkUser, I> input) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        String workflowId = workflows.startOrAttach(contract, input.apply(user), user.inn());
        log.info("Session started or attached: workflow={}, id={}", contract.workflowType(), workflowId);
        return workflowId;
    }

    /**
     * Проверяет допуск до открытия потока, затем ждёт результат workflow без ограничения 60 секунд
     * (предел — {@code await-timeout} workflow).
     */
    public <R> SseEmitter stream(WorkflowContract<?, R> contract, String workflowId, SessionEvents<R> events) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        return sse.send(() -> resolve(contract, workflowId, user, events), events::internalError);
    }

    private <R> SseEvent resolve(WorkflowContract<?, R> contract, String workflowId, LkUser user, SessionEvents<R> events) {
        if (!workflows.isOwnedBy(contract, workflowId, user.inn())) {
            log.warn("Session {} is requested by another organization", workflowId);
            return events.notFound();
        }
        try {
            return events.result(workflows.awaitResult(contract, workflowId));
        } catch (WorkflowNotFoundException e) {
            return events.notFound();
        } catch (WorkflowExecutionException e) {
            log.error("Session {} has no result", workflowId, e);
            return events.internalError();
        }
    }
}
