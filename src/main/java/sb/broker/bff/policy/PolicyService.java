package sb.broker.bff.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.access.ConsentPolicy;
import sb.broker.bff.access.LkAccessService;
import sb.broker.bff.access.LkUser;
import sb.broker.bff.policy.dto.SyncOrgPoliciesWorkflowResult;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.sse.SingleEventSseSender;
import sb.broker.bff.web.sse.SseEvent;
import sb.broker.bff.workflow.api.policy.SyncPoliciesInput;

/**
 * Полисы организации. Синхронизация — один {@code SyncPoliciesWorkflow} на ИНН:
 * POST и GET запускают его или подключаются к уже идущему.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyService {

    private static final String NO_POLICIES_MESSAGE = "policies.no-policies";

    private final LkAccessService access;
    private final WorkflowGateway workflows;
    private final SingleEventSseSender sse;
    private final Messages messages;

    /**
     * Запускает синхронизацию заранее, чтобы следующий GET получил список быстрее.
     */
    public void importPolicies() {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        log.info("Policy sync start: inn={}", user.inn());
        workflows.startOrAttach(LkWorkflows.SYNC_POLICIES, new SyncPoliciesInput(user.inn()), user.inn());
    }

    /**
     * Допуск и запуск синхронизации — до открытия потока: их сбой — {@code 500} JSON, как отказ Policy Profile
     * открыть поток. Список приходит одним событием.
     */
    public SseEmitter stream() {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        String workflowId = workflows.startOrAttach(
                LkWorkflows.SYNC_POLICIES, new SyncPoliciesInput(user.inn()), user.inn());
        log.info("Policy stream: workflowId={}", workflowId);
        return sse.send(() -> toEvent(workflows.awaitResult(LkWorkflows.SYNC_POLICIES, workflowId)),
                this::internalError);
    }

    private SseEvent toEvent(SyncOrgPoliciesWorkflowResult result) {
        log.info("Policy sync result: outcome={}", result.outcome());
        return switch (result.outcome()) {
            case COMPLETE -> SseEvent.complete(PolicyMapper.toDto(result.policies()));
            case NO_POLICIES -> SseEvent.error(new ErrorDTO(messages.get(NO_POLICIES_MESSAGE)));
        };
    }

    private SseEvent internalError() {
        log.error("Policy internal error");
        return SseEvent.error(messages.error(ErrorType.INTERNAL_ERROR));
    }
}
