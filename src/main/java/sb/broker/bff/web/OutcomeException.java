package sb.broker.bff.web;

import lombok.Getter;

/**
 * Прерывает обработку запроса бизнес-исходом (например, нет обязательного согласия).
 */
@Getter
public class OutcomeException extends RuntimeException {

    private final Outcome outcome;

    public OutcomeException(Outcome outcome) {
        super(outcome.name());
        this.outcome = outcome;
    }
}
