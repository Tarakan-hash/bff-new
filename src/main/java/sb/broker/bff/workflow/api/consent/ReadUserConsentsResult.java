package sb.broker.bff.workflow.api.consent;

import sb.broker.bff.workflow.api.access.AccessOutcome;

import java.util.List;

/**
 * Результат получения согласий. Отсутствие обязательного согласия не запрещает чтение списка.
 */
public record ReadUserConsentsResult(AccessOutcome outcome, List<UserConsent> consents) {

    public static ReadUserConsentsResult unauthorized() {
        return new ReadUserConsentsResult(AccessOutcome.UNAUTHORIZED, List.of());
    }
}
