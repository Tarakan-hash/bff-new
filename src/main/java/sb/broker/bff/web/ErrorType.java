package sb.broker.bff.web;

import org.springframework.http.HttpStatus;

/**
 * Ошибки, которые bff отдаёт телом {@code {"message": ...}}. Тексты — в {@code messages.properties}.
 */
public enum ErrorType {

    // === 400 ===
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_CONSENT_TYPE(HttpStatus.BAD_REQUEST),
    INVALID_CONSENT_DECISIONS(HttpStatus.BAD_REQUEST),
    PDN_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST),
    OPERATION_ID_MISSING(HttpStatus.BAD_REQUEST),
    PREVIEW_REQUEST_INVALID(HttpStatus.BAD_REQUEST),
    PREVIEW_ID_MISSING(HttpStatus.BAD_REQUEST),
    PREVIEW_QUERY_INVALID(HttpStatus.BAD_REQUEST),
    CALCULATION_OPERATION_ID_MISSING(HttpStatus.BAD_REQUEST),
    OFFER_ID_MISSING(HttpStatus.BAD_REQUEST),
    OFFER_NOT_FOUND(HttpStatus.BAD_REQUEST),
    OFFER_NOT_SELECTABLE(HttpStatus.BAD_REQUEST),

    // === 401 ===
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),

    // === 404 ===
    CONSENT_FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
    OFFER_SELECTION_UNAVAILABLE(HttpStatus.NOT_FOUND),

    // === 422 ===
    LEAD_SCENARIO_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT),

    // === 500 ===
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorType(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessageCode() {
        return "error." + name().toLowerCase().replace('_', '-');
    }
}
