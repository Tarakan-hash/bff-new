package sb.broker.bff.temporal;

/**
 * Workflow не дал результат: не запустился, завершился ошибкой, таймаутом, был отменён или прерван.
 */
public class WorkflowExecutionException extends RuntimeException {

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
