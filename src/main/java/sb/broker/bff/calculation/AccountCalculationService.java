package sb.broker.bff.calculation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sb.broker.bff.access.ConsentPolicy;
import sb.broker.bff.access.LkAccessService;
import sb.broker.bff.access.LkUser;
import sb.broker.bff.config.properties.CalculationProperties;
import sb.broker.security.AuthenticatedUser;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.UpdateCall;
import sb.broker.bff.temporal.WorkflowContract;
import sb.broker.bff.temporal.WorkflowExecutionException;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.temporal.WorkflowNotFoundException;
import sb.broker.bff.temporal.WorkflowUpdateRejectedException;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.OutcomeException;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.sse.SingleEventSseSender;
import sb.broker.bff.web.sse.SseEvent;
import sb.broker.bff.workflow.api.calculation.OfferSelection;
import sb.broker.bff.workflow.api.calculation.OfferSelectionOutcome;
import sb.broker.bff.workflow.api.calculation.OwnerRequest;
import sb.broker.bff.workflow.api.calculation.PreviewGate;
import sb.broker.bff.workflow.api.calculation.PreviewGateOutcome;
import sb.broker.bff.workflow.api.calculation.SelectOfferRequest;
import sb.broker.security.AuthenticatedUser;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Предрасчёты кабинета: старт с «воротами», поток с предложениями, выбор предложения. Продукты отличаются
 * контрактом workflow, телом запроса и сборкой события.
 *
 * <p>Владелец — в идентификаторе workflow ({@link CalculationOwners}): чужой или выдуманный {@code workflowId}
 * получает тот же ответ, что несуществующий. Workflow дополнительно сверяет email в валидаторах Update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountCalculationService {

    static final String AWAIT_GATE = "awaitGate";
    static final String AWAIT_PREVIEW = "awaitPreview";
    static final String SELECT_OFFER = "selectOffer";

    private final AuthenticatedUser authenticatedUser;
    private final LkAccessService access;
    private final WorkflowGateway workflows;
    private final CalculationOwners owners;
    private final CalculationProperties properties;
    private final SingleEventSseSender sse;
    private final Messages messages;

    /**
     * Запускает расчёт; допуск проверяет сам workflow. Ждёт «ворота» не дольше {@code await-timeout} workflow.
     *
     * @param input вход по email пользователя и {@code requestId} команды лида
     * @return идентификатор workflow при {@code accepted}; пусто — ворота не опубликованы за время ожидания
     */
    public <I> Optional<String> start(WorkflowContract<I, ?> contract, BiFunction<String, String, I> input) {
        String email = authenticatedUser.email();
        WorkflowGateway.StartedWithUpdate<PreviewGate> started = workflows.startWithUpdate(contract,
                input.apply(email, UUID.randomUUID().toString()), owners.key(email),
                new UpdateCall<>(AWAIT_GATE, new OwnerRequest(email), PreviewGate.class));
        if (started.result().isEmpty()) {
            log.warn("Calculation gates are not published in time: type={}, id={}",
                    contract.workflowType(), started.workflowId());
            return Optional.empty();
        }
        PreviewGateOutcome outcome = started.result().get().outcome();
        log.info("Calculation started: type={}, id={}, gate={}",
                contract.workflowType(), started.workflowId(), outcome);
        return switch (outcome) {
            case ACCEPTED -> Optional.of(started.workflowId());
            case UNAUTHORIZED -> throw new ServiceException(ErrorType.UNAUTHORIZED);
            case CONSENTS_REQUIRED -> throw new OutcomeException(Outcome.CONSENTS_REQUIRED);
            case GOSB_NOT_FOUND -> throw new OutcomeException(Outcome.PREVIEW_GOSB_NOT_FOUND);
            case REGION_MISMATCH -> throw new OutcomeException(Outcome.REGION_MISMATCH);
            case FAILED -> throw new ServiceException(ErrorType.INTERNAL_ERROR);
        };
    }

    /**
     * Допуск — до открытия потока; затем одно событие с предложениями. Пока workflow идёт, предложения
     * отдаёт Update {@code awaitPreview}; закрытый workflow — свой результат.
     *
     * @param gate     итог «ворот» из результата workflow
     * @param complete событие {@code complete} по предложениям
     */
    public <R> SseEmitter stream(WorkflowContract<?, R> contract, String workflowId,
                                 Function<R, PreviewGateOutcome> gate, Function<R, Object> complete) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        return sse.send(() -> preview(contract, workflowId, user.email(), gate, complete), this::internalError);
    }

    private <R> SseEvent preview(WorkflowContract<?, R> contract, String workflowId, String email,
                                 Function<R, PreviewGateOutcome> gate, Function<R, Object> complete) {
        if (!workflows.isOwnedBy(contract, workflowId, owners.key(email))) {
            log.warn("Calculation {} is requested by another user", workflowId);
            return notFound();
        }
        try {
            Optional<R> preview = workflows.update(contract, workflowId,
                    new UpdateCall<>(AWAIT_PREVIEW, new OwnerRequest(email), contract.resultType()),
                    properties.previewTimeout());
            return preview.map(result -> event(result, gate, complete)).orElseGet(this::internalError);
        } catch (WorkflowNotFoundException e) {
            return closed(contract, workflowId, gate, complete);
        } catch (WorkflowUpdateRejectedException e) {
            // валидатор пускает только владельца
            return notFound();
        } catch (WorkflowExecutionException e) {
            log.error("Calculation {} has no preview", workflowId, e);
            return internalError();
        }
    }

    /**
     * Workflow закрыт: предложения — из результата; отказ до предложений — «Расчёт не найден».
     */
    private <R> SseEvent closed(WorkflowContract<?, R> contract, String workflowId,
                                Function<R, PreviewGateOutcome> gate, Function<R, Object> complete) {
        try {
            return workflows.closedResult(contract, workflowId)
                    .map(result -> event(result, gate, complete))
                    .orElseGet(this::internalError);
        } catch (WorkflowNotFoundException e) {
            return notFound();
        } catch (WorkflowExecutionException e) {
            log.error("Calculation {} failed", workflowId, e);
            return internalError();
        }
    }

    /**
     * Предложения — {@code complete}; сбой расчёта — внутренняя ошибка; отказ до предложений — «Расчёт не найден».
     */
    private <R> SseEvent event(R result, Function<R, PreviewGateOutcome> gate, Function<R, Object> complete) {
        PreviewGateOutcome outcome = gate.apply(result);
        log.info("Calculation preview: gate={}", outcome);
        return switch (outcome) {
            case ACCEPTED -> SseEvent.complete(complete.apply(result));
            case FAILED -> internalError();
            default -> notFound();
        };
    }

    /**
     * Выбор предложения: допуск с согласиями, затем Update {@code selectOffer} не дольше {@code selection-timeout}.
     * Нет предложения или оно не {@code success} — {@code 400}; лид не обновлён — {@code 500}.
     *
     * @return пусто — за время ожидания итога нет (в том числе предложения ещё считаются), фронт повторяет запрос
     */
    public Optional<OfferSelectionOutcome> select(String workflowId, String offerId) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        WorkflowContract<?, ?> contract = LkWorkflows.CALCULATIONS.stream()
                .filter(candidate -> workflows.isOwnedBy(candidate, workflowId, owners.key(user.email())))
                .findFirst()
                .orElseThrow(() -> new ServiceException(ErrorType.OFFER_SELECTION_UNAVAILABLE));
        Optional<OfferSelectionOutcome> outcome;
        try {
            outcome = workflows.update(contract, workflowId,
                    new UpdateCall<>(SELECT_OFFER, new SelectOfferRequest(user.email(), offerId), OfferSelection.class),
                    properties.selectionTimeout()).map(OfferSelection::outcome);
        } catch (WorkflowNotFoundException | WorkflowUpdateRejectedException e) {
            log.warn("Offer selection not found: workflowId={}, offerId={}", workflowId, offerId);
            // закрыт, чужой, отказ или сбой до предложений
            throw new ServiceException(ErrorType.OFFER_SELECTION_UNAVAILABLE);
        } catch (WorkflowExecutionException e) {
            log.error("Offer selection in {} failed", workflowId, e);
            throw new ServiceException(ErrorType.INTERNAL_ERROR);
        }
        outcome.ifPresent(result -> {
            switch (result) {
                case ACCEPTED -> log.info("Offer selected: workflowId={}, offerId={}", workflowId, offerId);
                case OFFER_NOT_FOUND -> throw new ServiceException(ErrorType.OFFER_NOT_FOUND);
                case OFFER_NOT_SELECTABLE -> throw new ServiceException(ErrorType.OFFER_NOT_SELECTABLE);
                case FAILED -> throw new ServiceException(ErrorType.INTERNAL_ERROR);
            }
        });
        return outcome;
    }

    private SseEvent notFound() {
        return SseEvent.error(new ErrorDTO(messages.get("error.calculation-not-found")));
    }

    private SseEvent internalError() {
        return SseEvent.error(messages.error(ErrorType.INTERNAL_ERROR));
    }
}
