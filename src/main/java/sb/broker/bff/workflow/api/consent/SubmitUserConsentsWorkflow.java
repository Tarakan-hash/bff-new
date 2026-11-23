package sb.broker.bff.workflow.api.consent;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Подтверждение согласий пользователя через CRM Integration Service.
 */
@WorkflowInterface
public interface SubmitUserConsentsWorkflow {

    @WorkflowMethod(name = "SubmitUserConsentsWorkflow")
    void submit(SubmitUserConsentsInput input);
}
