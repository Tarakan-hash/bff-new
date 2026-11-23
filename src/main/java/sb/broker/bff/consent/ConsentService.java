package sb.broker.bff.consent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.access.ConsentPolicy;
import sb.broker.bff.access.LkAccessService;
import sb.broker.bff.access.LkUser;
import sb.broker.bff.config.properties.ConsentProperties;
import sb.broker.bff.consent.dto.ConsentDecisionDTO;
import sb.broker.bff.consent.dto.UserConsentDTO;
import sb.broker.security.AuthenticatedUser;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.consent.ConsentType;
import sb.broker.bff.workflow.api.consent.ReadConsentFileInput;
import sb.broker.bff.workflow.api.consent.ReadConsentFileResult;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsInput;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsResult;
import sb.broker.bff.workflow.api.consent.RecallUserConsentInput;
import sb.broker.bff.workflow.api.consent.SubmitUserConsentsInput;

import java.util.List;

/**
 * Согласия пользователя кабинета.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final AuthenticatedUser authenticatedUser;
    private final LkAccessService access;
    private final WorkflowGateway workflows;
    private final ConsentProperties properties;

    /**
     * Список согласий. Отсутствие обязательного согласия доступ не закрывает.
     */
    public List<UserConsentDTO> consents() {
        ReadUserConsentsResult result = workflows.execute(
                LkWorkflows.READ_USER_CONSENTS, new ReadUserConsentsInput(authenticatedUser.email()));
        if (result.outcome() != AccessOutcome.GRANTED) {
            log.warn("Read consents unauthorized: outcome={}", result.outcome());
            throw new ServiceException(ErrorType.UNAUTHORIZED);
        }
        return ConsentMapper.toDto(result.consents());
    }

    /**
     * Файл согласия. Нужен до проставления согласия, поэтому согласия не проверяются.
     */
    public ConsentFile file(ConsentType type) {
        ReadConsentFileResult result = workflows.execute(
                LkWorkflows.READ_CONSENT_FILE, new ReadConsentFileInput(authenticatedUser.email(), type));
        return switch (result.outcome()) {
            case GRANTED -> new ConsentFile(result.content(), contentType(result.contentType()));
            case UNAUTHORIZED -> {
                log.warn("Consent file unauthorized: type={}", type);
                throw new ServiceException(ErrorType.UNAUTHORIZED);
            }
            case NOT_FOUND -> {
                log.warn("Consent file not found: type={}", type);
                throw new ServiceException(ErrorType.CONSENT_FILE_NOT_FOUND);
            }
        };
    }

    /**
     * Передаёт в CRM отмеченные согласия. Доступен без обязательного согласия: им его и дают.
     */
    public void submit(List<ConsentDecisionDTO> decisions) {
        validate(decisions);
        LkUser user = access.require(ConsentPolicy.IGNORE);
        List<ConsentType> accepted = decisions.stream()
                .filter(ConsentDecisionDTO::consent)
                .map(ConsentDecisionDTO::type)
                .toList();
        log.info("Submitting accepted consents: types={}", accepted);
        workflows.execute(LkWorkflows.SUBMIT_USER_CONSENTS, new SubmitUserConsentsInput(user.email(), accepted));
    }

    /**
     * Отзывает согласие. Повторный отзыв уже отозванного согласия успешен.
     */
    public void recall(ConsentType type) {
        LkUser user = access.require(ConsentPolicy.ENFORCE);
        workflows.execute(LkWorkflows.RECALL_USER_CONSENT, new RecallUserConsentInput(user.email(), type));
        log.info("Consent recalled: type={}", type);
        // отозванное обязательное согласие должно закрыть кабинет сразу, а не по истечении кэша
        access.evictCurrentUser();
    }

    private static void validate(List<ConsentDecisionDTO> decisions) {
        long distinctTypes = decisions.stream().map(ConsentDecisionDTO::type).distinct().count();
        if (distinctTypes != decisions.size()) {
            throw new ServiceException(ErrorType.INVALID_CONSENT_DECISIONS);
        }
        boolean pdnAccepted = decisions.stream()
                .anyMatch(decision -> decision.type() == ConsentType.PDN && decision.consent());
        if (!pdnAccepted) {
            throw new ServiceException(ErrorType.PDN_CONSENT_REQUIRED);
        }
    }

    private MediaType contentType(String crmContentType) {
        return MediaType.parseMediaType(crmContentType == null || crmContentType.isBlank()
                ? properties.defaultFileContentType()
                : crmContentType);
    }
}
