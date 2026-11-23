package sb.broker.bff.catalog;

import lombok.Getter;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;

/**
 * Записи справочника нет. Текст ответа {@code 404} — {@code error.catalog-not-found.<справочник>}.
 */
@Getter
public class CatalogEntryNotFoundException extends RuntimeException {

    private final CatalogDictionary dictionary;

    public CatalogEntryNotFoundException(CatalogDictionary dictionary) {
        super(dictionary.name());
        this.dictionary = dictionary;
    }

    public String getMessageCode() {
        return "error.catalog-not-found." + dictionary.name().toLowerCase().replace('_', '-');
    }
}
