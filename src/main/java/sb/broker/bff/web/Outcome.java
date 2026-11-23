package sb.broker.bff.web;

import org.springframework.http.HttpStatus;

/**
 * Бизнес-исходы, которые bff отдаёт телом {@code {"outcome": ..., "message": ...}}.
 */
public enum Outcome {

    ACCEPTED(HttpStatus.OK),
    CONSENTS_REQUIRED(HttpStatus.OK),
    REGION_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
    REGION_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    GOSB_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    PRODUCT_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT),
    /** Предрасчёт: тот же код {@code gosb_not_found}, свой текст плана. */
    PREVIEW_GOSB_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "gosb_not_found");

    private final HttpStatus status;
    private final String code;

    Outcome(HttpStatus status) {
        this(status, null);
    }

    Outcome(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** Значение поля {@code outcome} в ответе. */
    public String getCode() {
        return code != null ? code : name().toLowerCase();
    }

    public String getMessageCode() {
        return "outcome." + name().toLowerCase().replace('_', '-');
    }
}
