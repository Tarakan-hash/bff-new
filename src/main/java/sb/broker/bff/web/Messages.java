package sb.broker.bff.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import sb.broker.bff.web.dto.ErrorDTO;
import sb.broker.bff.web.dto.OutcomeDTO;

/**
 * Тексты ответов из {@code messages.properties}.
 */
@Component
@RequiredArgsConstructor
public class Messages {

    private final MessageSource messageSource;

    public String get(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }

    public String find(String code) {
        return messageSource.getMessage(code, null, null, LocaleContextHolder.getLocale());
    }

    public ErrorDTO error(ErrorType errorType) {
        return new ErrorDTO(get(errorType.getMessageCode()));
    }

    public OutcomeDTO outcome(Outcome outcome) {
        String message = outcome == Outcome.ACCEPTED ? null : get(outcome.getMessageCode());
        return new OutcomeDTO(outcome.getCode(), message);
    }
}
