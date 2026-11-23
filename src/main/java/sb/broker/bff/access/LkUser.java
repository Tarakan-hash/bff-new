package sb.broker.bff.access;

/**
 * Пользователь кабинета, получивший допуск.
 *
 * @param inn   ИНН организации пользователя ({@code clientId})
 * @param email email пользователя
 */
public record LkUser(String inn, String email) {
}
