package sb.broker.bff.temporal;

/**
 * Вызов Update workflow.
 *
 * @param name       имя Update (имя метода интерфейса)
 * @param argument   единственный аргумент
 * @param resultType тип результата
 */
public record UpdateCall<U>(String name, Object argument, Class<U> resultType) {
}
