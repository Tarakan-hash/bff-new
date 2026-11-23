package sb.broker.bff.workflow.api.consent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Согласие контакта CRM. JSON-контракт одинаков в BFF, WDS и CRM Integration Service.
 *
 * @param title название согласия
 * @param type тип согласия (XML_ID)
 * @param status признак подтверждения
 * @param required обязательность согласия
 * @param guid идентификатор согласия
 * @param date дата получения в ISO 8601 со смещением; может отсутствовать
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserConsent(String title, String type, boolean status, boolean required, String guid, String date) {

    /**
     * Совместимость с вызовами прежней четырёхполевой модели из подключённой библиотеки.
     */
    public UserConsent(String title, boolean status, boolean required, String date) {
        this(title, null, status, required, null, date);
    }
}
