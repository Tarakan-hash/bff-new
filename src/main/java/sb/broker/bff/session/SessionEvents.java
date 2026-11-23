package sb.broker.bff.session;

import sb.broker.bff.web.sse.SseEvent;

/**
 * Как результат асинхронной сессии превращается в событие SSE.
 *
 * @param <R> тип результата workflow
 */
public interface SessionEvents<R> {

    /** Событие по результату workflow. */
    SseEvent result(R result);

    /** Workflow не найден или принадлежит другой организации. */
    SseEvent notFound();

    /** Workflow завершился ошибкой, не дал результат вовремя или событие не удалось записать. */
    SseEvent internalError();
}
