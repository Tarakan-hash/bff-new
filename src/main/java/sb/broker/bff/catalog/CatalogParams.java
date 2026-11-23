package sb.broker.bff.catalog;

/**
 * Форматы параметров справочников. Значения передаются в Product Catalog как пришли.
 */
final class CatalogParams {

    static final String UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    static final String INTEGER = "^-?\\d{1,10}$";
    static final String BOOLEAN = "^(true|false)$";
    static final String VEHICLE_PRODUCT_TYPE = "^(kasko|osago)$";
    static final String INSURANCE_PRODUCT_TYPE = "^(dms|kasko|osago)$";
    static final int KLADR_LENGTH = 13;
    static final int GOSB_ID_MAX_LENGTH = 255;

    private CatalogParams() {
    }
}
