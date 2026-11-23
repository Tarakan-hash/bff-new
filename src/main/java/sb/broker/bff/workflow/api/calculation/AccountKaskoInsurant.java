package sb.broker.bff.workflow.api.calculation;

/**
 * Страхователь КАСКО. Пустой ГОСБ определяется по региону до подготовки расчёта.
 */
public record AccountKaskoInsurant(String companyType, String inn, Integer regionCode,
        AccountContacts contacts, String product, String gosb) {

    public AccountKaskoInsurant(String companyType, String inn, Integer regionCode,
            AccountContacts contacts, String product) {
        this(companyType, inn, regionCode, contacts, product, null);
    }
}
