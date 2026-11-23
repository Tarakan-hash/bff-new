package sb.broker.bff.companyprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.companyprofile.dto.CompanyProfileErrorDTO;
import sb.broker.bff.session.ProfileSessions;
import sb.broker.bff.session.SessionEvents;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.web.Messages;
import sb.broker.bff.web.sse.SseEvent;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfileResult;
import sb.broker.bff.workflow.api.companyprofile.InitiateCompanyProfileInput;

/**
 * Карточка компании кабинета. Повтор POST — новая сессия и новый сбор.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyProfileService implements SessionEvents<CompanyProfileResult> {

    static final String SESSION_NOT_FOUND = "SESSION_NOT_FOUND";
    static final String INTERNAL_FALLBACK = "INTERNAL_FALLBACK";

    private final ProfileSessions sessions;
    private final Messages messages;

    public String initiate() {
        return sessions.start(LkWorkflows.INITIATE_COMPANY_PROFILE, user -> new InitiateCompanyProfileInput(user.inn()));
    }

    public SseEmitter stream(String workflowId) {
        return sessions.stream(LkWorkflows.INITIATE_COMPANY_PROFILE, workflowId, this);
    }

    @Override
    public SseEvent result(CompanyProfileResult result) {
        boolean found = result.profile() != null;
        log.info("Company profile result: found={}", found);
        return result.profile() != null
                ? SseEvent.complete(CompanyProfileMapper.toDto(result.profile()))
                : error(result.error().name());
    }

    @Override
    public SseEvent notFound() {
        return error(SESSION_NOT_FOUND);
    }

    @Override
    public SseEvent internalError() {
        return error(INTERNAL_FALLBACK);
    }

    /**
     * Текст ошибки — {@code company-profile.<код>} из {@code messages.properties}.
     */
    private SseEvent error(String code) {
        log.warn("Company profile error: code={}", code);
        String messageCode = "company-profile." + code.toLowerCase().replace('_', '-');
        return SseEvent.error(new CompanyProfileErrorDTO(code, messages.get(messageCode)));
    }
}
