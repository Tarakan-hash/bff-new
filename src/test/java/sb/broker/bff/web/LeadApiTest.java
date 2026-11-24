package sb.broker.bff.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import sb.broker.bff.catalog.ProductCatalogService;
import sb.broker.bff.lead.dto.CatalogProduct;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowAwaitTimeoutException;
import sb.broker.bff.workflow.api.lead.LeadOutcome;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadInput;
import sb.broker.bff.workflow.api.lead.SubmitAccountLeadResult;
import sb.broker.bff.workflow.api.catalog.CatalogDictionary;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LeadApiTest extends ApiTestSupport {

    @MockitoBean
    private ProductCatalogService productCatalog;

    private static final String WORKFLOW_ID = "lk-lead-1";
    private static final String LEAD = """
            {"fio":"Иванов Иван Иванович","phone":"+79990000000","comment":"","subtype":"CONTACT_BROKER"}
            """;

    @Test
    void acceptedLeadIs200() throws Exception {
        leadOutcomeIs(LeadOutcome.ACCEPTED);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"outcome":"accepted"}""", org.springframework.test.json.JsonCompareMode.STRICT));

        // пустой comment — пустой комментарий
        verify(workflows).start(LkWorkflows.SUBMIT_ACCOUNT_LEAD, new SubmitAccountLeadInput(
                EMAIL, "Иванов Иван Иванович", "+79990000000", "", "CONTACT_BROKER", null, null, null, null), null);
    }

    @Test
    void contactBrokerWithLegacyProductIdReachesWorkflow() throws Exception {
        leadOutcomeIs(LeadOutcome.ACCEPTED);
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                {"fio":"Иванов Иван Иванович","phone":"+79990000000","comment":"Нужна консультация",
                 "subtype":"CONTACT_BROKER","productId":3}
                """)).andExpect(status().isOk());
        verify(workflows).start(LkWorkflows.SUBMIT_ACCOUNT_LEAD, new SubmitAccountLeadInput(
                EMAIL, "Иванов Иван Иванович", "+79990000000", "Нужна консультация",
                "CONTACT_BROKER", null, null, null, null), null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCT_CATALOG", "RECOMMENDATION"})
    void selectedProductsReachLeadWithoutContactFields(String subtype) throws Exception {
        leadOutcomeIs(LeadOutcome.ACCEPTED);
        when(productCatalog.read(CatalogDictionary.PRODUCTS, Map.of())).thenReturn(List.of(
                new CatalogProduct(7L, "Грузы и перевозки"), new CatalogProduct(8L, "Строительство и монтаж")));
        String comment = """
                Добрый день!
                Прошу связаться со мной для консультации по продуктам:

                Грузы и перевозки
                Пункт отправления:
                Пункт назначения:
                Номенклатура груза:
                Стоимость груза:
                Вид транспорта:

                Строительство и монтаж
                Объект строительства:
                Стоимость проекта:""";
        String body = JsonMapper.builder().build().writeValueAsString(Map.of(
                "subtype", subtype, "productIds", List.of(7L, 8L), "comment", comment));

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("accepted"));

        ArgumentCaptor<SubmitAccountLeadInput> input = ArgumentCaptor.forClass(SubmitAccountLeadInput.class);
        verify(workflows).start(eq(LkWorkflows.SUBMIT_ACCOUNT_LEAD), input.capture(), isNull());
        assertThat(input.getValue().subtype()).isEqualTo(subtype);
        assertThat(input.getValue().fio()).isNull();
        assertThat(input.getValue().phone()).isNull();
        assertThat(input.getValue().productId()).isNull();
        assertThat(input.getValue().policyNumber()).isNull();
        assertThat(input.getValue().comment()).isEqualTo(comment
                + "\nВыбранные продукты: Грузы и перевозки, Строительство и монтаж");
    }

    @Test
    void selectedProductMissingInCatalogKeepsItsIdInComment() throws Exception {
        leadOutcomeIs(LeadOutcome.ACCEPTED);
        when(productCatalog.read(CatalogDictionary.PRODUCTS, Map.of()))
                .thenReturn(List.of(new CatalogProduct(7L, "Грузы и перевозки")));

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"subtype":"PRODUCT_CATALOG","productIds":[7,8]}
                        """))
                .andExpect(status().isOk());

        ArgumentCaptor<SubmitAccountLeadInput> input = ArgumentCaptor.forClass(SubmitAccountLeadInput.class);
        verify(workflows).start(eq(LkWorkflows.SUBMIT_ACCOUNT_LEAD), input.capture(), isNull());
        assertThat(input.getValue().comment()).isEqualTo("\nВыбранные продукты: Грузы и перевозки, ID 8");
    }

    @Test
    void selectedProductsCannotContainNull() throws Exception {
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"subtype":"PRODUCT_CATALOG","productIds":[7,null]}
                        """))
                .andExpect(status().isUnprocessableContent());

        verifyNoInteractions(workflows, productCatalog);
    }

    @Test
    void regionNotFoundIs422() throws Exception {
        leadOutcomeIs(LeadOutcome.REGION_NOT_FOUND);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().json("""
                        {"outcome":"region_not_found","message":"Регион компании не найден"}"""));
    }

    @Test
    void gosbNotFoundIs422() throws Exception {
        leadOutcomeIs(LeadOutcome.GOSB_NOT_FOUND);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.outcome").value("gosb_not_found"));
    }

    @Test
    void unauthorizedLeadIs401() throws Exception {
        leadOutcomeIs(LeadOutcome.UNAUTHORIZED);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void slowLeadIs204() throws Exception {
        when(workflows.start(eq(LkWorkflows.SUBMIT_ACCOUNT_LEAD), any(), isNull())).thenReturn(WORKFLOW_ID);
        when(workflows.awaitResult(LkWorkflows.SUBMIT_ACCOUNT_LEAD, WORKFLOW_ID))
                .thenThrow(new WorkflowAwaitTimeoutException(WORKFLOW_ID, null));

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(LEAD))
                .andExpect(status().isNoContent());
    }

    @Test
    @Disabled("Добавить проверку на несколько Product id")
    void prolongationPassesPolicyFields() throws Exception {
        leadOutcomeIs(LeadOutcome.ACCEPTED);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fio":"Иванов","phone":"+79990000000","comment":"Нужно продлить","subtype":"POLICY_PROLONGATION",
                         "productId":10,"policyNumber":"КАСКО-2026/014","insuranceGroup":"","insuranceCompany":"СК Пример"}
                        """))
                .andExpect(status().isOk());

        verify(workflows).start(LkWorkflows.SUBMIT_ACCOUNT_LEAD, new SubmitAccountLeadInput(EMAIL, "Иванов",
                "+79990000000", "Нужно продлить", "POLICY_PROLONGATION", 10L, "КАСКО-2026/014", "", "СК Пример"), null);
    }

    @Test
    @Disabled("ID может быть более одного для продукта")
    void productNotFoundIs422() throws Exception {
        leadOutcomeIs(LeadOutcome.PRODUCT_NOT_FOUND);

        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION","productId":99,"policyNumber":"N"}
                        """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().json("""
                        {"outcome":"product_not_found","message":"Продукт не найден"}"""));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // сценарий закрыт
            """
            {"fio":"Иванов","phone":"+7","subtype":"QUOTATION"}""",
            // у CONTACT_BROKER поля полиса или продукта
            """
            {"fio":"Иванов","phone":"+7","subtype":"CONTACT_BROKER","productIds":[10]}""",
            """
            {"fio":"Иванов","phone":"+7","subtype":"CONTACT_BROKER","insuranceGroup":""}""",
            // у продления нет productId
            """
            {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION","policyNumber":"N"}"""})
    @Disabled(value = "Поддержка требуется на несколько продуктID")
    void unavailableScenarioIs422WithoutTemporal(String body) throws Exception {
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Сценарий создания лида недоступен"));

        verifyNoInteractions(workflows);
    }

    @Disabled("Добавить проверку на несколько Product id")
    @ParameterizedTest
    @ValueSource(strings = {
            // productId не целое число
            """
            {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION","productId":1.5,"policyNumber":"N"}""",
            """
            {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION","productId":"10","policyNumber":"N"}""",
            // нет непустого номера полиса — 400 раньше 422 за отсутствующий productId
            """
            {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION","productId":10,"policyNumber":" "}""",
            """
            {"fio":"Иванов","phone":"+7","subtype":"POLICY_PROLONGATION"}"""})
    void malformedProlongationIs400WithoutTemporal(String body) throws Exception {
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запрос не разобран"));

        verifyNoInteractions(workflows);
    }

    @Test
    @Disabled
    void blankFioIs400() throws Exception {
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"fio":" ","phone":"+79990000000","subtype":"CONTACT_BROKER"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запрос не разобран"));
    }

    @Test
    void nonObjectBodyIs400() throws Exception {
        mvc.perform(post("/api/v1/leads").with(user()).contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Запрос не разобран"));
    }

    private void leadOutcomeIs(LeadOutcome outcome) {
        when(workflows.start(eq(LkWorkflows.SUBMIT_ACCOUNT_LEAD), any(), isNull())).thenReturn(WORKFLOW_ID);
        when(workflows.awaitResult(LkWorkflows.SUBMIT_ACCOUNT_LEAD, WORKFLOW_ID))
                .thenReturn(new SubmitAccountLeadResult(outcome));
    }
}
