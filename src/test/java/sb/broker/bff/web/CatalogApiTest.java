package sb.broker.bff.web;

import org.junit.jupiter.api.Test;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;
import sb.broker.bff.workflow.api.catalog.CatalogOutcome;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogInput;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogResult;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiTest extends ApiTestSupport {

    private static final String CATEGORY_ID = "6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11";

    @Test
    void dictionaryIsReturnedAsIs() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any())).thenReturn(new ReadProductCatalogResult(
                CatalogOutcome.FOUND,
                Map.of("name", "B", "contents", List.of(Map.of("context", "short", "value", "Легковые")))));

        mvc.perform(get("/api/v1/vehicle/categories/{id}", CATEGORY_ID).param("full", "true").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"name":"B","contents":[{"context":"short","value":"Легковые"}]}"""));

        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.VEHICLE_CATEGORY, Map.of("categoryId", CATEGORY_ID, "full", "true")));
    }

    @Test
    void absentOptionalParamsAreNotPassed() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(new ReadProductCatalogResult(CatalogOutcome.FOUND, List.of()));

        mvc.perform(get("/api/v1/vehicle/use_purpose_types").param("productType", "osago").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.VEHICLE_USE_PURPOSE_TYPES, Map.of("productType", "osago")));
    }

    @Test
    void notFoundIs404WithDictionaryMessage() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(ReadProductCatalogResult.of(CatalogOutcome.NOT_FOUND));

        mvc.perform(get("/api/v1/vehicle/categories/{id}", CATEGORY_ID).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Категория транспортного средства не найдена"));
    }

    @Test
    void unauthorizedIs401() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(ReadProductCatalogResult.of(CatalogOutcome.UNAUTHORIZED));

        mvc.perform(get("/api/v1/regions").with(user()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidUuidIs400WithoutTemporal() throws Exception {
        mvc.perform(get("/api/v1/vehicle/documents/{id}", "not-a-uuid").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Идентификатор типа документа не является UUID"));

        verifyNoInteractions(workflows);
    }

    @Test
    void invalidBooleanIs400() throws Exception {
        mvc.perform(get("/api/v1/vehicle/categories").param("isSpecTech", "yes").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Признак спецтехники не является логическим значением"));
    }

    @Test
    void kladrMustHave13Characters() throws Exception {
        mvc.perform(get("/api/v1/city/{kladrId}", "770000000000").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("КЛАДР не состоит из 13 символов"));
    }

    @Test
    void notIntegratedCompaniesRequireProductType() throws Exception {
        mvc.perform(get("/api/v1/insurance_company/not_integrated").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Тип страхового продукта не передан или некорректен"));
    }

    @Test
    void literalPathWinsOverIdentifier() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(new ReadProductCatalogResult(CatalogOutcome.FOUND, List.of()));

        mvc.perform(get("/api/v1/insurance_company/not_integrated").param("productType", "dms").with(user()))
                .andExpect(status().isOk());

        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.NOT_INTEGRATED_INSURANCE_COMPANIES, Map.of("productType", "dms")));
    }

    @Test
    void parametersRejectedByProductCatalogAre400() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(ReadProductCatalogResult.of(CatalogOutcome.INVALID_REQUEST));

        mvc.perform(get("/api/v1/regions/{regionCode}", "9999999999").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запрос не разобран"));
    }

    @Test
    void insuranceCategoryPassesIdAndFlag() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any())).thenReturn(new ReadProductCatalogResult(
                CatalogOutcome.FOUND, Map.of("categoryId", 2, "products", List.of())));

        mvc.perform(get("/api/v1/insurance_categories/{id}", "2").param("showInCategory", "true").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(2));

        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.INSURANCE_CATEGORY, Map.of("categoryId", "2", "showInCategory", "true")));
    }

    @Test
    void insuranceCategoriesAndProducts() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(new ReadProductCatalogResult(CatalogOutcome.FOUND, List.of()));

        mvc.perform(get("/api/v1/insurance_categories").param("includeProducts", "true").with(user()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products").with(user()))
                .andExpect(status().isOk());

        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.INSURANCE_CATEGORIES, Map.of("includeProducts", "true")));
        verify(workflows).execute(LkWorkflows.READ_PRODUCT_CATALOG, new ReadProductCatalogInput(
                EMAIL, CatalogDictionary.PRODUCTS, Map.of()));
    }

    @Test
    void insuranceCategoryNotFoundAndInvalidId() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(ReadProductCatalogResult.of(CatalogOutcome.NOT_FOUND));

        mvc.perform(get("/api/v1/insurance_categories/{id}", "99").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Категория страхования не найдена"));
        mvc.perform(get("/api/v1/insurance_categories/{id}", "abc").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Идентификатор категории страхования не является целым числом"));
        mvc.perform(get("/api/v1/insurance_categories").param("showInCatalog", "1").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Признак каталога не является логическим значением"));
    }
}
