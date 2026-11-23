package sb.broker.bff.workflow.api.consent;

/**
 * @param email email пользователя кабинета из проверенного контекста авторизации
 */
public record ReadUserConsentsInput(String email) {
}
