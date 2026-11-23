package sb.broker.bff.temporal;

/**
 * Update не дал результата: валидатор отклонил его или обработчик завершился ошибкой. Различить нельзя:
 * оба приходят как {@code WorkflowUpdateException} при получении результата, а с шифрованием payload'ов текст
 * ошибки — «Encoded failure» (ошибку Update SDK не расшифровывает). Поэтому workflow сообщают бизнес-исходы
 * результатом Update, а ошибкой — только «недоступно».
 */
public class WorkflowUpdateRejectedException extends WorkflowExecutionException {

    private final String code;

    public WorkflowUpdateRejectedException(String workflowId, String code, Throwable cause) {
        super("Update rejected by " + workflowId + ": " + code, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
