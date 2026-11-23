package sb.broker.bff.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import sb.broker.bff.catalog.CatalogEntryNotFoundException;
import sb.broker.bff.temporal.WorkflowExecutionException;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.dto.OutcomeDTO;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Перевод исключений в ответы кабинета.
 *
 * <p>Content-Type задаётся явно: так JSON-ошибка отдаётся и методам, которые в успехе открывают
 * {@code text/event-stream} или отдают файл.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private static final String TYPE_MISMATCH_CODE_PREFIX = "error.validation.";

    private final Messages messages;

    /** Ошибки получения обязательного claim текущего пользователя — тот же HTTP-контракт 401. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorDTO> handle(AuthenticationException e) {
        log.warn("Authentication failed", e);
        return error(ErrorType.UNAUTHORIZED);
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorDTO> handle(ServiceException e) {
        log.warn("Service error: type={}", e.getErrorType());
        return error(e.getErrorType());
    }

    @ExceptionHandler(OutcomeException.class)
    public ResponseEntity<OutcomeDTO> handle(OutcomeException e) {
        log.info("Outcome: {}", e.getOutcome());
        return json(e.getOutcome().getStatus(), messages.outcome(e.getOutcome()));
    }

    @ExceptionHandler(CatalogEntryNotFoundException.class)
    public ResponseEntity<ErrorDTO> handle(CatalogEntryNotFoundException e) {
        log.warn("Catalog entry not found: code={}", e.getMessageCode());
        return json(HttpStatus.NOT_FOUND, new ErrorDTO(messages.get(e.getMessageCode())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDTO> handle(MethodArgumentNotValidException e) {
        log.warn("Request validation failed: errors={}", e.getBindingResult().getAllErrors().size());
        return badRequest(e.getBindingResult().getAllErrors().stream());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorDTO> handle(HandlerMethodValidationException e) {
        log.warn("Method validation failed: errors={}", e.getAllErrors().size());
        return badRequest(e.getAllErrors().stream());
    }

    /**
     * Неверный тип path/query-параметра. Текст — {@code error.validation.<параметр>},
     * иначе общий текст некорректного запроса.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDTO> handle(MethodArgumentTypeMismatchException e) {
        log.warn("Parameter type mismatch: {}", e.getName());
        String message = Objects.requireNonNullElseGet(
                messages.find(TYPE_MISMATCH_CODE_PREFIX + e.getName()),
                () -> messages.get(ErrorType.INVALID_REQUEST.getMessageCode()));
        return json(ErrorType.INVALID_REQUEST.getStatus(), new ErrorDTO(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDTO> handle(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        log.warn("Request body is not readable: cause={}", cause != null ? cause.getClass().getSimpleName() : "none");
        return error(ErrorType.INVALID_REQUEST);
    }

    @ExceptionHandler(WorkflowExecutionException.class)
    public ResponseEntity<ErrorDTO> handle(WorkflowExecutionException e) {
        log.error("Workflow failure", e);
        return error(ErrorType.INTERNAL_ERROR);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handle(AsyncRequestNotUsableException e) {
        log.warn("Client disconnected: {}", e.getMessage());
    }

    /**
     * Исключения Spring MVC со своим статусом (404 маршрута, 405 метода и т.п.) отдаются с этим статусом,
     * остальное — {@code 500}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDTO> handle(Exception e, HttpServletResponse servletResponse) {
        if (servletResponse.isCommitted()) {
            log.warn("Exception occurred after response was already committed", e);
            return null;
        }
        if (e instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
            log.warn("Client error: status={}", response.getStatusCode().value(), e);
            String detail = response.getBody().getDetail();
            return json(response.getStatusCode(), detail != null
                    ? new ErrorDTO(detail)
                    : messages.error(ErrorType.INVALID_REQUEST));
        }
        log.error("Unexpected error", e);
        return error(ErrorType.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorDTO> badRequest(Stream<? extends MessageSourceResolvable> errors) {
        List<String> details = errors.map(MessageSourceResolvable::getDefaultMessage).filter(Objects::nonNull).toList();
        return details.isEmpty()
                ? error(ErrorType.INVALID_REQUEST)
                : json(ErrorType.INVALID_REQUEST.getStatus(), new ErrorDTO(details.getFirst()));
    }

    private ResponseEntity<ErrorDTO> error(ErrorType errorType) {
        return json(errorType.getStatus(), messages.error(errorType));
    }

    private static <T> ResponseEntity<T> json(HttpStatusCode status, T body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
