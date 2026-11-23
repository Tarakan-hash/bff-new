package sb.broker.bff.temporal;

import io.temporal.common.metadata.POJOWorkflowInterfaceMetadata;

/**
 * Типизированное описание workflow: интерфейс из bff-lk-workflow-api, тип входа и результата.
 *
 * <p>Тип workflow вычисляется по метаданным интерфейса так же, как это делает Temporal SDK,
 * поэтому имя всегда совпадает с тем, под которым реализация зарегистрирована в worker'е.
 *
 * @param <I> тип входа
 * @param <R> тип результата; {@link Void} для workflow без результата
 */
public record WorkflowContract<I, R>(String workflowType, Class<R> resultType) {

    public static <I, R> WorkflowContract<I, R> of(Class<?> workflowInterface,
                                                   Class<I> inputType,
                                                   Class<R> resultType) {
        String type = POJOWorkflowInterfaceMetadata.newInstance(workflowInterface)
                .getWorkflowMethod()
                .orElseThrow(() -> new IllegalArgumentException(workflowInterface + " has no @WorkflowMethod"))
                .getName();
        return new WorkflowContract<>(type, resultType);
    }
}
