package sb.broker.bff.workflow.api.consent;

import java.util.List;

/**
 * @param email email пользователя кабинета
 * @param types типы согласий, которые пользователь подтвердил
 */
public record SubmitUserConsentsInput(String email, List<ConsentType> types) {
}
