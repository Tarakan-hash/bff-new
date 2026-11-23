package sb.broker.bff.calculation.dms;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sb.broker.bff.calculation.AccountCalculationService;
import sb.broker.bff.calculation.dms.dto.AccountDmsCalculationWorkflowInput;
import sb.broker.bff.calculation.dms.dto.CreateAccountDmsPreviewRequestDTO;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;

import java.util.Optional;

/**
 * Предрасчёт ДМС кабинета: тело HTTP → вход workflow, результат workflow → событие с отбором и сводками.
 */
@Service
@RequiredArgsConstructor
public class DmsCalculationService {

    private final AccountCalculationService calculations;

    /**
     * @return идентификатор workflow; пусто — «ворота» не опубликованы за время ожидания
     */
    public Optional<String> preview(CreateAccountDmsPreviewRequestDTO request) {
        return calculations.start(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION,
                (email, requestId) -> new AccountDmsCalculationWorkflowInput(email, requestId, toWorkflow(request)));
    }

    public SseEmitter stream(String workflowId, String page, String size, String sortBy, String sortDirection,
                             String options) {
        DmsPreviewQuery query = DmsPreviewQuery.parse(page, size, sortBy, sortDirection, options);
        return calculations.stream(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                DmsPreviewResult::gate, result -> DmsPreviewAssembler.assemble(result, query));
    }

    private static AccountDmsCalculationWorkflowInput.Request toWorkflow(CreateAccountDmsPreviewRequestDTO request) {
        return new AccountDmsCalculationWorkflowInput.Request(request.companyType(), null, request.regionCode(), request.surname(),
                request.name(), request.phone(),
                request.employees().stream()
                        .map(group -> new AccountDmsCalculationWorkflowInput.EmployeeGroup(group.count(), group.ageGroupId(), group.kladrId()))
                        .toList(),
                request.product());
    }
}
