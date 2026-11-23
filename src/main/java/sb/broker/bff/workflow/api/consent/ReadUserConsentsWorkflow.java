package sb.broker.bff.workflow.api.consent;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Получение согласий пользователя через CRM Integration Service.
 */
@WorkflowInterface
public interface ReadUserConsentsWorkflow {

    @WorkflowMethod(name = "ReadUserConsentsWorkflow")
    ReadUserConsentsResult read(ReadUserConsentsInput input);
}
