package sb.broker.bff.temporal;

/**
 * Workflow ещё выполняется, а время ожидания результата истекло.
 */
public class WorkflowAwaitTimeoutException extends WorkflowExecutionException {

    public WorkflowAwaitTimeoutException(String workflowId, Throwable cause) {
        super("Workflow result was not received in time: " + workflowId, cause);
    }
}
