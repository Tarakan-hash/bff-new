package sb.broker.bff.web;

import lombok.Getter;

/**
 * Ошибка с известным HTTP-статусом и текстом из {@link ErrorType}.
 */
@Getter
public class ServiceException extends RuntimeException {

    private final ErrorType errorType;

    public ServiceException(ErrorType errorType) {
        super(errorType.name());
        this.errorType = errorType;
    }
}
