package sb.broker.bff.web.sse;

/**
 * Одно событие SSE.
 *
 * @param name имя события ({@code complete}, {@code error})
 * @param data объект для сериализации в JSON или готовая JSON-строка
 */
public record SseEvent(String name, Object data) {

    public static final String COMPLETE = "complete";
    public static final String ERROR = "error";

    public static SseEvent complete(Object data) {
        return new SseEvent(COMPLETE, data);
    }

    public static SseEvent error(Object data) {
        return new SseEvent(ERROR, data);
    }
}
