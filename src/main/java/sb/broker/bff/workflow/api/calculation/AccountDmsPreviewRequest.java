package sb.broker.bff.workflow.api.calculation;

import java.util.List;

/**
 * Предрасчёт ДМС. Пустой ГОСБ определяется по региону до подготовки расчёта.
 */
public record AccountDmsPreviewRequest(String companyType, String inn, Integer regionCode,
        String surname, String name, String phone, List<AccountDmsEmployee> employees, String product, String gosb) {

    public AccountDmsPreviewRequest(String companyType, String inn, Integer regionCode,
            String surname, String name, String phone, List<AccountDmsEmployee> employees, String product) {
        this(companyType, inn, regionCode, surname, name, phone, employees, product, null);
    }
}
