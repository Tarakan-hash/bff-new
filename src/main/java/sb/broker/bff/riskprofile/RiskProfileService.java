package sb.broker.bff.riskprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.session.ProfileSessions;
import sb.broker.bff.session.SessionEvents;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.sse.SseEvent;
import sb.broker.bff.workflow.api.riskprofile.InitiateRiskProfileInput;
import sb.broker.bff.workflow.api.riskprofile.RiskProfileResult;

/**
 * Риск-профиль и рекомендации кабинета.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskProfileService implements SessionEvents<RiskProfileResult> {

    private final ProfileSessions sessions;
    private final Messages messages;

    /**
     * Повтор, пока расчёт для ИНН идёт, подключается к нему: второй расчёт не создаётся.
     */
    public String initiate() {
        log.info("Risk profile initiate");
        return sessions.startOrAttach(LkWorkflows.INITIATE_RISK_PROFILE,
                user -> new InitiateRiskProfileInput(user.inn()));
    }

    public SseEmitter stream(String workflowId) {
        log.info("Risk profile stream: workflowId={}", workflowId);
        return sessions.stream(LkWorkflows.INITIATE_RISK_PROFILE, workflowId, this);
    }

    @Override
    public SseEvent result(RiskProfileResult result) {
        if (result.code() == RiskProfileResult.OK) {
            log.info("Risk profile result: OK");
            return SseEvent.complete(RiskProfileMapper.toDto(result.profile()));
        }
        log.warn("Risk profile result: code={}, message={}", result.code(), result.message());
        return SseEvent.error(new ErrorDTO(result.message()));
    }

    @Override
    public SseEvent notFound() {
        log.warn("Risk profile session not found");
        return internalError();
    }

    @Override
    public SseEvent internalError() {
        log.error("Risk profile internal error");
        return SseEvent.error(messages.error(ErrorType.INTERNAL_ERROR));
    }
}
