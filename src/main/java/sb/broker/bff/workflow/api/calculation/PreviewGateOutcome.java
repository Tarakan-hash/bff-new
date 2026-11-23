package sb.broker.bff.workflow.api.calculation;

/**
 * Результат проверок до запуска расчёта.
 */
public enum PreviewGateOutcome {
    ACCEPTED,
    UNAUTHORIZED,
    CONSENTS_REQUIRED,
    GOSB_NOT_FOUND,
    FAILED,
    REGION_MISMATCH
}
