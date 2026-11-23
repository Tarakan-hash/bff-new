package sb.broker.bff.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sb.broker.bff.web.openapi.ErrorExample;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;

import java.util.LinkedHashMap;
import java.util.Map;

import static sb.broker.bff.catalog.CatalogParams.BOOLEAN;
import static sb.broker.bff.catalog.CatalogParams.GOSB_ID_MAX_LENGTH;
import static sb.broker.bff.catalog.CatalogParams.INSURANCE_PRODUCT_TYPE;
import static sb.broker.bff.catalog.CatalogParams.INTEGER;
import static sb.broker.bff.catalog.CatalogParams.KLADR_LENGTH;
import static sb.broker.bff.catalog.CatalogParams.UUID;
import static sb.broker.bff.catalog.CatalogParams.VEHICLE_PRODUCT_TYPE;

/**
 * Справочники Product Catalog. Путь и параметры совпадают с Product Catalog,
 * тело ответа справочника возвращается без изменений.
 */
@Tag(name = "Справочники")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProductCatalogController {

    private final ProductCatalogService catalog;

    @Operation(summary = "Категории транспортных средств")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.is-spec-tech", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @GetMapping("/vehicle/categories")
    public Object vehicleCategories(
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.is-spec-tech}") String isSpecTech) {
        return catalog.read(CatalogDictionary.VEHICLE_CATEGORIES, params("isSpecTech", isSpecTech));
    }

    @Operation(summary = "Категория транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.category", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.vehicle-category", when = "Записи нет")
    @GetMapping("/vehicle/categories/{categoryId}")
    public Object vehicleCategory(
            @PathVariable @Pattern(regexp = UUID, message = "{error.validation.category}") String categoryId,
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.category}") String full) {
        return catalog.read(CatalogDictionary.VEHICLE_CATEGORY, params("categoryId", categoryId, "full", full));
    }

    @Operation(summary = "Типы документов транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.is-spec-tech", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @GetMapping("/vehicle/documents")
    public Object vehicleDocuments(
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.is-spec-tech}") String isSpecTech) {
        return catalog.read(CatalogDictionary.VEHICLE_DOCUMENTS, params("isSpecTech", isSpecTech));
    }

    @Operation(summary = "Тип документа транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.document-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.vehicle-document", when = "Записи нет")
    @GetMapping("/vehicle/documents/{documentId}")
    public Object vehicleDocument(
            @PathVariable @Pattern(regexp = UUID, message = "{error.validation.document-id}") String documentId) {
        return catalog.read(CatalogDictionary.VEHICLE_DOCUMENT, params("documentId", documentId));
    }

    @Operation(summary = "Марки транспортных средств")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/vehicle/marks")
    public Object vehicleMarks() {
        return catalog.read(CatalogDictionary.VEHICLE_MARKS, Map.of());
    }

    @Operation(summary = "Модели марки транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.mark-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.vehicle-models", when = "Записи нет")
    @GetMapping("/vehicle/models/{markId}")
    public Object vehicleModels(
            @PathVariable @Pattern(regexp = INTEGER, message = "{error.validation.mark-id}") String markId) {
        return catalog.read(CatalogDictionary.VEHICLE_MODELS, params("markId", markId));
    }

    @Operation(summary = "Цели использования транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.use-purpose-types", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @GetMapping("/vehicle/use_purpose_types")
    public Object vehicleUsePurposeTypes(
            @RequestParam(required = false)
            @Pattern(regexp = VEHICLE_PRODUCT_TYPE, message = "{error.validation.use-purpose-types}") String productType,
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.use-purpose-types}") String isSpecTech) {
        return catalog.read(CatalogDictionary.VEHICLE_USE_PURPOSE_TYPES,
                params("productType", productType, "isSpecTech", isSpecTech));
    }

    @Operation(summary = "Цель использования транспортного средства")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.use-purpose-type-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.vehicle-use-purpose-type", when = "Записи нет")
    @GetMapping("/vehicle/use_purpose_types/{usePurposeTypeId}")
    public Object vehicleUsePurposeType(
            @PathVariable
            @Pattern(regexp = UUID, message = "{error.validation.use-purpose-type-id}") String usePurposeTypeId) {
        return catalog.read(CatalogDictionary.VEHICLE_USE_PURPOSE_TYPE, params("usePurposeTypeId", usePurposeTypeId));
    }

    @Operation(summary = "Возрастные группы")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/age_group")
    public Object ageGroups() {
        return catalog.read(CatalogDictionary.AGE_GROUPS, Map.of());
    }

    @Operation(summary = "Возрастная группа")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.age-group-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.age-group", when = "Записи нет")
    @GetMapping("/age_group/{ageGroupId}")
    public Object ageGroup(
            @PathVariable @Pattern(regexp = INTEGER, message = "{error.validation.age-group-id}") String ageGroupId) {
        return catalog.read(CatalogDictionary.AGE_GROUP, params("ageGroupId", ageGroupId));
    }

    @Operation(summary = "Города")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/city")
    public Object cities() {
        return catalog.read(CatalogDictionary.CITIES, Map.of());
    }

    @Operation(summary = "Город по КЛАДР")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.kladr-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.city", when = "Записи нет")
    @GetMapping("/city/{kladrId}")
    public Object city(
            @PathVariable
            @Size(min = KLADR_LENGTH, max = KLADR_LENGTH, message = "{error.validation.kladr-id}") String kladrId) {
        return catalog.read(CatalogDictionary.CITY, params("kladrId", kladrId));
    }

    @Operation(summary = "Регионы")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/regions")
    public Object regions() {
        return catalog.read(CatalogDictionary.REGIONS, Map.of());
    }

    @Operation(summary = "Регион по коду")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.region-code", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.region", when = "Записи нет")
    @GetMapping("/regions/{regionCode}")
    public Object region(
            @PathVariable @Pattern(regexp = INTEGER, message = "{error.validation.region-code}") String regionCode) {
        return catalog.read(CatalogDictionary.REGION, params("regionCode", regionCode));
    }

    @Operation(summary = "Регионы по ГОСБ")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.gosb-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.regions-by-gosb", when = "Записи нет")
    @GetMapping("/regions/gosb/{gosbId}")
    public Object regionByGosb(
            @PathVariable
            @NotBlank(message = "{error.validation.gosb-id}")
            @Size(max = GOSB_ID_MAX_LENGTH, message = "{error.validation.gosb-id}") String gosbId) {
        return catalog.read(CatalogDictionary.REGIONS_BY_GOSB, params("gosbId", gosbId));
    }

    @Operation(summary = "ГОСБ региона")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.region-code", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.gosb", when = "Записи нет")
    @GetMapping("/gosb/{regionCode}")
    public Object gosb(
            @PathVariable @Pattern(regexp = INTEGER, message = "{error.validation.region-code}") String regionCode) {
        return catalog.read(CatalogDictionary.GOSB, params("regionCode", regionCode));
    }

    @Operation(summary = "Страховые компании")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/insurance_company")
    public Object insuranceCompanies() {
        return catalog.read(CatalogDictionary.INSURANCE_COMPANIES, Map.of());
    }

    @Operation(summary = "Неинтегрированные страховые компании")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.product-type", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @GetMapping("/insurance_company/not_integrated")
    public Object notIntegratedInsuranceCompanies(
            @RequestParam(required = false)
            @NotNull(message = "{error.validation.product-type}")
            @Pattern(regexp = INSURANCE_PRODUCT_TYPE, message = "{error.validation.product-type}") String productType) {
        return catalog.read(CatalogDictionary.NOT_INTEGRATED_INSURANCE_COMPANIES, params("productType", productType));
    }

    @Operation(summary = "Страховая компания")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.insurance-company-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.insurance-company", when = "Записи нет")
    @GetMapping("/insurance_company/{insuranceCompanyId}")
    public Object insuranceCompany(
            @PathVariable
            @Pattern(regexp = INTEGER, message = "{error.validation.insurance-company-id}") String insuranceCompanyId) {
        return catalog.read(CatalogDictionary.INSURANCE_COMPANY, params("insuranceCompanyId", insuranceCompanyId));
    }

    @Operation(summary = "Продукты брокера")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @GetMapping("/products")
    public Object products() {
        return catalog.read(CatalogDictionary.PRODUCTS, Map.of());
    }

    @Operation(summary = "Категории страхования")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.include-products", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @GetMapping("/insurance_categories")
    public Object insuranceCategories(
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.include-products}") String includeProducts,
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.show-in-catalog}") String showInCatalog) {
        return catalog.read(CatalogDictionary.INSURANCE_CATEGORIES,
                params("includeProducts", includeProducts, "showInCatalog", showInCatalog));
    }

    @Operation(summary = "Категория страхования с продуктами")
    @ApiResponse(responseCode = "200", description = "Ответ справочника Product Catalog без изменений")
    @ErrorExample(status = "400", message = "error.validation.insurance-category-id", when = "Параметр некорректен")
    @ErrorExample(status = "400", message = "error.invalid-request", when = "Product Catalog отверг параметры")
    @ErrorExample(status = "404", message = "error.catalog-not-found.insurance-category", when = "Записи нет")
    @GetMapping("/insurance_categories/{categoryId}")
    public Object insuranceCategory(
            @PathVariable
            @Pattern(regexp = INTEGER, message = "{error.validation.insurance-category-id}") String categoryId,
            @RequestParam(required = false)
            @Pattern(regexp = BOOLEAN, message = "{error.validation.show-in-category}") String showInCategory) {
        return catalog.read(CatalogDictionary.INSURANCE_CATEGORY,
                params("categoryId", categoryId, "showInCategory", showInCategory));
    }

    /**
     * Параметры справочника: пары имя-значение, отсутствующие значения не передаются.
     */
    private static Map<String, String> params(String... namesAndValues) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] != null) {
                params.put(namesAndValues[i], namesAndValues[i + 1]);
            }
        }
        return params;
    }
}
