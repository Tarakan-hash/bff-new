package sb.broker.bff.lead;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import sb.broker.bff.catalog.ProductCatalogService;
import sb.broker.bff.config.properties.LeadProperties;
import sb.broker.bff.lead.dto.AccountLeadRequestDTO;
import sb.broker.bff.lead.dto.CatalogProduct;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;
import sb.broker.security.AuthenticatedUser;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowAwaitTimeoutException;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.web.ErrorType;
import sb.broker.bff.web.Outcome;
import sb.broker.bff.web.ServiceException;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadInput;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Лиды кабинета. Допуск проверяет сам {@code SubmitAccountLeadWorkflow}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeadService {

    private final AuthenticatedUser authenticatedUser;
    private final WorkflowGateway workflows;
    private final LeadProperties properties;
    private final ProductCatalogService productCatalog;
    private final ObjectMapper mapper;

    /**
     * @return итог команды; пусто, если workflow не завершился за время ожидания
     */
    public Optional<Outcome> submit(AccountLeadRequestDTO request) {
        LeadProperties.Scenario scenario = scenario(request);
        SubmitAccountLeadInput input = scenario.policy()
                ? new SubmitAccountLeadInput(authenticatedUser.email(), request.fio(), request.phone(),  (request.comment() == null ? "" : request.comment()) +
                        getSelectedProductComment(request.productIds()),
                request.subtype(), request.productIds().getFirst(), request.policyNumber(),
                        request.insuranceGroup(), request.insuranceCompany())
                : new SubmitAccountLeadInput(authenticatedUser.email(), request.fio(), request.phone(),
                        request.comment(), request.subtype(), null, null, null, null);

        String workflowId = workflows.start(LkWorkflows.SUBMIT_ACCOUNT_LEAD, input, null);
        SubmitAccountLeadResult result;
        try {
            result = workflows.awaitResult(LkWorkflows.SUBMIT_ACCOUNT_LEAD, workflowId);
        } catch (WorkflowAwaitTimeoutException e) {
            log.warn("Lead workflow timeout: workflowId={}", workflowId);
            return Optional.empty();
        }
        log.info("Lead result: outcome={}", result.outcome());
        return Optional.of(switch (result.outcome()) {
            case ACCEPTED -> Outcome.ACCEPTED;
            case CONSENTS_REQUIRED -> Outcome.CONSENTS_REQUIRED;
            case REGION_NOT_FOUND -> Outcome.REGION_NOT_FOUND;
            case GOSB_NOT_FOUND -> Outcome.GOSB_NOT_FOUND;
            case PRODUCT_NOT_FOUND -> Outcome.PRODUCT_NOT_FOUND;
            case UNAUTHORIZED -> throw new ServiceException(ErrorType.UNAUTHORIZED);
        });
    }

    private String getSelectedProductComment(ArrayList<Long> ids) {
        StringBuilder comment = new StringBuilder("\nВыбранные продукты: ");
        Object data = productCatalog.read(CatalogDictionary.PRODUCTS, Map.of());
        List<CatalogProduct> products = mapper.convertValue(
                data, new TypeReference<List<CatalogProduct>>() {});
        Map<Long, String> namesById = products.stream()
                .collect(Collectors.toMap(CatalogProduct::id, CatalogProduct::name));

        for (Long id : ids) {
            comment.append(namesById.get(id)).append(", ");
        }
        return comment.toString();
    }

    /**
     * Порядок проверок плана: сначала {@code 400} (нет непустого номера полиса у продления), затем {@code 422}
     * (сценарий закрыт, у заявки без полиса переданы его поля, у продления нет продукта).
     */
    private LeadProperties.Scenario scenario(AccountLeadRequestDTO request) {
        Optional<LeadProperties.Scenario> scenario = properties.scenario(request.subtype());
        if (scenario.filter(LeadProperties.Scenario::policy).isPresent()
                && (request.policyNumber() == null || request.policyNumber().isBlank())) {
            throw new ServiceException(ErrorType.INVALID_REQUEST);
        }
        if (scenario.isEmpty()
                || !scenario.get().policy() && request.hasPolicyFields()
                || scenario.get().policy() && (request.productIds() == null || request.productIds().isEmpty()
                        || request.productIds().stream().anyMatch(java.util.Objects::isNull))) {
            throw new ServiceException(ErrorType.LEAD_SCENARIO_UNAVAILABLE);
        }
        return scenario.get();
    }
}
