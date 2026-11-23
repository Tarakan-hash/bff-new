package sb.broker.bff.temporal;

/**
 * Workflow с указанным идентификатором не существует (или уже удалён по retention).
 */
public class WorkflowNotFoundException extends WorkflowExecutionException {

    public WorkflowNotFoundException(String workflowId, Throwable cause) {
        super("Workflow not found: " + workflowId, cause);
    }
}
