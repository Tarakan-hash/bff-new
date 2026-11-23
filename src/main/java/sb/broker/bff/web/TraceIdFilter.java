package sb.broker.bff.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Идентификатор запроса для логов: {@code trace-id} в MDC попадает в каждую строку JSON-лога,
 * поэтому строки одного запроса (контроллер → сервис → workflow → ошибка) читаются вместе даже
 * при одновременной обработке других запросов.
 *
 * <p>Если ingress или вышестоящий сервис прислал {@code X-Request-Id}, используется он: цепочка
 * логов остаётся общей между сервисами. Иначе генерируется свой идентификатор.
 *
 * <p>Фоновые потоки (SSE в {@code SingleEventSseSender}) получают копию контекста явно —
 * MDC привязан к потоку и сам не передаётся.
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "trace-id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // SSE и другие async-запросы делают повторный dispatch тем же запросом — идентификатор один на весь запрос
        String traceId = (String) request.getAttribute(TRACE_ID);
        if (traceId == null) {
            String incoming = request.getHeader(REQUEST_ID_HEADER);
            traceId = incoming != null && !incoming.isBlank()
                    ? sanitize(incoming)
                    : UUID.randomUUID().toString().replace("-", "");
            request.setAttribute(TRACE_ID, traceId);
        }
        MDC.put(TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    /**
     * Внешний заголовок — не доверяем: только безопасные символы, максимум 64 символа
     * (HMAC-хеш, UUID, короткий GUID).
     */
    private static String sanitize(String header) {
        return header.replaceAll("[^a-zA-Z0-9._-]", "-").substring(0, Math.min(64, header.length()));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator/");
    }
}

