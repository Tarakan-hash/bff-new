package sb.broker.bff.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import sb.broker.bff.calculation.CalculationOwners;
import sb.broker.bff.calculation.dms.dto.AccountDmsCalculationWorkflowInput;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.UpdateCall;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.temporal.WorkflowNotFoundException;
import sb.broker.bff.temporal.WorkflowUpdateRejectedException;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.calculation.AccountDmsEmployee;
import sb.broker.bff.workflow.api.calculation.DmsPreviewOffer;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;
import sb.broker.bff.workflow.api.calculation.InitiateAccountKaskoCalculationInput;
import sb.broker.bff.workflow.api.calculation.InitiateAccountOsagoCalculationInput;
import sb.broker.bff.workflow.api.calculation.KaskoPreviewOffer;
import sb.broker.bff.workflow.api.calculation.KaskoPreviewResult;
import sb.broker.bff.workflow.api.calculation.OfferSelection;
import sb.broker.bff.workflow.api.calculation.OfferSelectionOutcome;
import sb.broker.bff.workflow.api.calculation.PreviewGate;
import sb.broker.bff.workflow.api.calculation.PreviewGateOutcome;
import sb.broker.bff.workflow.api.calculation.VehicleKind;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CalculationApiTest extends ApiTestSupport {

    private static final String BODY = """
            {"companyType":"company","regionCode":77,"surname":"Иванов","name":"Иван",
             "phone":"+74951234567","employees":[{"count":10,"ageGroupId":1,"kladrId":"7700000000000"}],"product":"dms"}""";

    @Autowired
    private CalculationOwners owners;

    private String workflowId;

    @BeforeEach
    void ownWorkflow() {
        workflowId = "lk-calc-dms-" + owners.key(EMAIL) + "-1";
        when(workflows.isOwnedBy(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION), eq(workflowId), eq(owners.key(EMAIL))))
                .thenReturn(true);
    }

    // ===== POST предрасчёта =====

    @Test
    void acceptedGateIs202WithWorkflowIdOfOwner() throws Exception {
        gateIs(Optional.of(PreviewGateOutcome.ACCEPTED));

        mvc.perform(post("/api/v1/calculation/dms/preview").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"workflowId\":\"" + workflowId + "\"}"));

        verify(workflows).startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION),
                argThat((AccountDmsCalculationWorkflowInput input) -> input.email().equals(EMAIL)
                        && input.request().inn() == null
                        && input.request().employees().equals(List.of(new AccountDmsCalculationWorkflowInput.EmployeeGroup(10, 1L, "7700000000000")))
                        && input.requestId() != null),
                eq(owners.key(EMAIL)), any());
    }

    @Test
    void gateOutcomesMapToPlanResponses() throws Exception {
        gateIs(Optional.of(PreviewGateOutcome.GOSB_NOT_FOUND));
        postPreview().andExpect(status().isUnprocessableContent())
                .andExpect(content().json("{\"outcome\":\"gosb_not_found\",\"message\":\"ГОСБ по региону не найден\"}"));

        gateIs(Optional.of(PreviewGateOutcome.REGION_MISMATCH));
        postPreview().andExpect(status().isUnprocessableContent())
                .andExpect(content().json("{\"outcome\":\"region_mismatch\",\"message\":\"Выбран некорректный регион\"}"));

        gateIs(Optional.of(PreviewGateOutcome.CONSENTS_REQUIRED));
        postPreview().andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("consents_required"));

        gateIs(Optional.of(PreviewGateOutcome.UNAUTHORIZED));
        postPreview().andExpect(status().isUnauthorized());

        gateIs(Optional.of(PreviewGateOutcome.FAILED));
        postPreview().andExpect(status().isInternalServerError());

        gateIs(Optional.empty());
        postPreview().andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"email\":\"x@example.com\"", "\"inn\":\"7707083893\"", "\"consents\":[]", "\"product\":\"kasko\"", "\"employees\":[]",
            "\"companyType\":\"bank\""})
    void rejectedBodyIs400WithoutTemporal(String field) throws Exception {
        String body = BODY.replaceFirst("\\{", "{" + field + ",");
        if (field.startsWith("\"product\"") || field.startsWith("\"employees\"") || field.startsWith("\"companyType\"")) {
            body = BODY.replaceAll("\"" + field.substring(1, field.indexOf('"', 1)) + "\":(\"[^\"]*\"|\\[[^]]*])", field);
        }
        mvc.perform(post("/api/v1/calculation/dms/preview").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Тело запроса не принято"));
        verifyNoInteractions(workflows);
    }

    // ===== GET предрасчёта =====

    @Test
    void runningCalculationStreamsPreviewPage() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.update(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION), eq(workflowId), any(UpdateCall.class), any()))
                .thenReturn(Optional.of(preview()));

        assertThat(stream("/api/v1/calculation/dms/" + workflowId + "?size=1&sortBy=totalCost&sortDirection=desc"))
                .startsWith("event:complete")
                .contains("\"offers\":[{\"offerId\":\"o-2\"")
                .contains("\"summary\":[{\"insuranceCompanyId\":1");
    }

    @Test
    void closedCalculationStreamsItsResult() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.update(any(), eq(workflowId), any(), any())).thenThrow(new WorkflowNotFoundException(workflowId, null));
        when(workflows.closedResult(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId)).thenReturn(Optional.of(preview()));

        assertThat(stream("/api/v1/calculation/dms/" + workflowId)).startsWith("event:complete");
    }

    @Test
    void refusedOrForeignCalculationIsNotFoundEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.update(any(), eq(workflowId), any(), any())).thenThrow(new WorkflowNotFoundException(workflowId, null));
        when(workflows.closedResult(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId))
                .thenReturn(Optional.of(DmsPreviewResult.refused(PreviewGateOutcome.GOSB_NOT_FOUND)));

        assertThat(stream("/api/v1/calculation/dms/" + workflowId)).contains("Расчёт не найден");
        when(workflows.closedResult(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId))
                .thenReturn(Optional.of(DmsPreviewResult.refused(PreviewGateOutcome.FAILED)));
        assertThat(stream("/api/v1/calculation/dms/" + workflowId)).contains("Внутренняя ошибка сервиса");
        assertThat(stream("/api/v1/calculation/dms/lk-calc-dms-" + owners.key("other@example.com") + "-1"))
                .contains("Расчёт не найден");
        verify(workflows, never()).update(any(), eq("lk-calc-dms-" + owners.key("other@example.com") + "-1"), any(), any());
    }

    @Test
    void invalidQueryIs400BeforeStream() throws Exception {
        mvc.perform(get("/api/v1/calculation/dms/" + workflowId).param("options", "spa").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Параметры отбора предрасчёта некорректны"));
    }

    // ===== КАСКО =====

    private static final String KASKO = """
            {"insurant":{"companyType":"company","inn":"7707083893","regionCode":77,
               "contacts":{"surname":"Иванов","name":"Иван","phone":"+74951234567"},"product":"kasko"},
             "vehicle":{"vin":"XTA00000000000001","category":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11","markId":1,"mark":"Lada",
               "modelId":2,"model":"Vesta","manufactureYear":2024,"enginePower":106,"mileage":1000,
               "document":{"typeGuid":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12","number":"77УН123456","issueDate":"2024-05-01"},
               "usePurpose":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13"},
             "insuranceContract":{"startDate":"2026-10-08","insuranceCost":1500000}}""";

    @Test
    void kaskoPreviewStartsWorkflowAndStreamsOffersAsIs() throws Exception {
        String kaskoId = "lk-calc-kasko-" + owners.key(EMAIL) + "-1";
        when(workflows.startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION), any(), eq(owners.key(EMAIL)), any()))
                .thenReturn(new WorkflowGateway.StartedWithUpdate<>(kaskoId, Optional.of(PreviewGate.of(PreviewGateOutcome.ACCEPTED))));
        mvc.perform(post("/api/v1/calculation/kasko").with(user()).contentType(MediaType.APPLICATION_JSON).content(KASKO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflowId").value(kaskoId));
        verify(workflows).startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION),
                argThat((InitiateAccountKaskoCalculationInput input) ->
                        input.request().insuranceContract().startDate().equals("2026-10-08")
                                && input.request().insurant().contacts().surname().equals("Иванов")),
                eq(owners.key(EMAIL)), any());

        accessIs(AccessOutcome.GRANTED);
        when(workflows.isOwnedBy(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION), eq(kaskoId), eq(owners.key(EMAIL))))
                .thenReturn(true);
        when(workflows.update(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION), eq(kaskoId), any(UpdateCall.class), any()))
                .thenReturn(Optional.of(new KaskoPreviewResult(PreviewGateOutcome.ACCEPTED, "calc-1", List.of(
                        new KaskoPreviewOffer("o-1", "error", "нет ответа", 7L, null)))));
        assertThat(stream("/api/v1/calculation/kasko/" + kaskoId)).startsWith("event:complete")
                .contains("{\"calculationId\":\"calc-1\",\"offers\":[{\"offerId\":\"o-1\",\"status\":\"error\","
                        + "\"errorMessage\":\"нет ответа\",\"insuranceCompanyId\":7,\"totalCost\":null}]}");
    }

    @Test
    void kaskoBodyWithEmailOrWrongProductIs400() throws Exception {
        mvc.perform(post("/api/v1/calculation/kasko").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(KASKO.replaceFirst("\\{", "{\"email\":\"x@example.com\",")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/calculation/kasko").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(KASKO.replace("\"product\":\"kasko\"", "\"product\":\"osago\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Тело запроса не принято"));
        verifyNoInteractions(workflows);
    }

    // ===== ОСАГО =====

    private static final String DOCUMENT = """
            {"typeGuid":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f12","series":"4510","number":"123456","issueDate":"2020-01-15"}""";
    private static final String COMPANY = """
            {"company":{"inn":"7707083893","contacts":{"phone":"+7"},"shortName":"ООО","fullName":"ООО","ogrn":"1","kpp":"2",
             "opf":12300,"address":"Москва","identityDocument":%s}}""".formatted(DOCUMENT);
    private static final String SPECIAL = """
            {"representative":{"regionCode":77,"contacts":{"surname":"Иванов","name":"Иван","phone":"+7"}},
             "insurant":%s,
             "vehicles":[{"vin":"XTA00000000000001","chassisNumber":"CH-1","category":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f11",
               "markId":1,"mark":"Кран","modelId":2,"model":"КС","manufactureYear":2020,"enginePower":300,
               "document":%s,"usePurpose":"6f1e8c2a-3b47-4d9e-8a15-2c6b7d0e4f13","owner":%s,"ownerIsInsurant":true}],
             "insuranceContract":{"startDateOsago":"2026-10-08","endDateOsago":"2027-10-07"}}""";

    @Test
    void specialVehicleStartsOsagoWorkflowWithSpecialKind() throws Exception {
        when(workflows.startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_OSAGO_CALCULATION), any(), eq(owners.key(EMAIL)), any()))
                .thenReturn(new WorkflowGateway.StartedWithUpdate<>("lk-calc-osago-x",
                        Optional.of(PreviewGate.of(PreviewGateOutcome.ACCEPTED))));

        mvc.perform(post("/api/v1/calculation/osago/special-vehicle").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(SPECIAL.formatted(COMPANY, DOCUMENT, COMPANY)))
                .andExpect(status().isAccepted());

        verify(workflows).startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_OSAGO_CALCULATION),
                argThat((InitiateAccountOsagoCalculationInput input) -> input.vehicleKind() == VehicleKind.SPECIAL
                        && "CH-1".equals(input.request().vehicles().getFirst().chassisNumber())
                        && input.request().insurant().company() != null),
                eq(owners.key(EMAIL)), any());
    }

    @Test
    void insurantAndOwnerNeedExactlyOneBranch() throws Exception {
        String both = COMPANY.substring(0, COMPANY.length() - 1) + ",\"entrepreneur\":" + COMPANY.substring(11);
        mvc.perform(post("/api/v1/calculation/osago/special-vehicle").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(SPECIAL.formatted(both, DOCUMENT, COMPANY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Тело запроса не принято"));
        mvc.perform(post("/api/v1/calculation/osago/special-vehicle").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(SPECIAL.formatted(COMPANY, DOCUMENT, "{}")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(workflows);
    }

    // ===== выбор предложения =====

    @Test
    void acceptedSelectionIs200() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        selectionIs(Optional.of(new OfferSelection("o-1", OfferSelectionOutcome.ACCEPTED)));

        selectOffer(workflowId, "o-1").andExpect(status().isOk()).andExpect(content().string(""));
    }

    @Test
    void selectionOutcomesMapToPlanResponses() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        selectionIs(Optional.of(new OfferSelection("o-1", OfferSelectionOutcome.FAILED)));
        selectOffer(workflowId, "o-1").andExpect(status().isInternalServerError());

        selectionIs(Optional.empty());
        selectOffer(workflowId, "o-1").andExpect(status().isNoContent());

        selectionIs(Optional.of(new OfferSelection("o-1", OfferSelectionOutcome.OFFER_NOT_FOUND)));
        selectOffer(workflowId, "o-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Предложение не найдено"));

        selectionIs(Optional.of(new OfferSelection("o-1", OfferSelectionOutcome.OFFER_NOT_SELECTABLE)));
        selectOffer(workflowId, "o-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Предложение нельзя выбрать"));

        // текст отказа может быть зашифрован: любой отказ — расчёт недоступен
        rejectedWith("Encoded failure");
        selectOffer(workflowId, "o-1").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Выбор предложения недоступен"));
    }

    @Test
    void foreignOrUnknownWorkflowIs404WithoutUpdate() throws Exception {
        accessIs(AccessOutcome.GRANTED);

        selectOffer("lk-calc-dms-" + owners.key("other@example.com") + "-1", "o-1").andExpect(status().isNotFound());
        selectOffer("lk-risk-profile-7707083893", "o-1").andExpect(status().isNotFound());
        verify(workflows, never()).update(any(), any(), any(), any());
    }

    @Test
    void selectionWithoutConsentsIs200OutcomeAndEmptyIdsAre400() throws Exception {
        accessIs(AccessOutcome.CONSENTS_REQUIRED);
        selectOffer(workflowId, "o-1").andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("consents_required"));

        mvc.perform(post("/api/v1/calculation/offer").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"\",\"offerId\":\"o\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Идентификатор операции не передан"));
        mvc.perform(post("/api/v1/calculation/offer").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"w\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Идентификатор предложения не передан"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "\"1\"", "9223372036854775808"})
    void dmsAgeGroupRequiresJsonLong(String value) throws Exception {
        mvc.perform(post("/api/v1/calculation/dms/preview").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"ageGroupId\":1", "\"ageGroupId\":" + value)))
                .andExpect(status().isBadRequest());
        verify(workflows, never()).startWithUpdate(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2025-02-29", "2026-13-01"})
    void osagoDocumentRequiresCalendarDate(String value) throws Exception {
        mvc.perform(post("/api/v1/calculation/osago/special-vehicle").with(user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SPECIAL.formatted(COMPANY, DOCUMENT, COMPANY).replace("2020-01-15", value)))
                .andExpect(status().isBadRequest());
        verify(workflows, never()).startWithUpdate(any(), any(), any(), any());
    }

    private void gateIs(Optional<PreviewGateOutcome> outcome) {
        when(workflows.startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION), any(), eq(owners.key(EMAIL)), any()))
                .thenReturn(new WorkflowGateway.StartedWithUpdate<>(workflowId, outcome.map(PreviewGate::of)));
    }

    private void selectionIs(Optional<OfferSelection> selection) {
        when(workflows.update(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION), eq(workflowId), any(UpdateCall.class), any()))
                .thenReturn(selection);
    }

    private void rejectedWith(String code) {
        when(workflows.update(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION), eq(workflowId), any(UpdateCall.class), any()))
                .thenThrow(new WorkflowUpdateRejectedException(workflowId, code, null));
    }

    private org.springframework.test.web.servlet.ResultActions postPreview() throws Exception {
        return mvc.perform(post("/api/v1/calculation/dms/preview").with(user()).contentType(MediaType.APPLICATION_JSON).content(BODY));
    }

    private org.springframework.test.web.servlet.ResultActions selectOffer(String id, String offerId) throws Exception {
        return mvc.perform(post("/api/v1/calculation/offer").with(user()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"workflowId\":\"" + id + "\",\"offerId\":\"" + offerId + "\"}"));
    }

    private String stream(String path) throws Exception {
        MvcResult started = mvc.perform(get(path).with(user()).accept("text/event-stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);
        return mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static DmsPreviewResult preview() {
        return new DmsPreviewResult(PreviewGateOutcome.ACCEPTED, "calc-1",
                List.of(new DmsPreviewOffer("o-1", "success", 1L, "7700000000000", new BigDecimal("100"), 3, List.of("clinic")),
                        new DmsPreviewOffer("o-2", "success", 1L, "7700000000000", new BigDecimal("200"), 4, List.of("clinic"))),
                List.of(new AccountDmsEmployee(10, 1L, "7700000000000")));
    }

    @Test
    void dmsGosbIsPassedToWorkflow() throws Exception {
        gateIs(Optional.of(PreviewGateOutcome.ACCEPTED));
        mvc.perform(post("/api/v1/calculation/dms/preview").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"regionCode\":77", "\"regionCode\":77,\"gosb\":\"38\"")))
                .andExpect(status().isAccepted());
        verify(workflows).startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION),
                argThat((AccountDmsCalculationWorkflowInput input) -> "38".equals(input.request().gosb())
                        && input.request().inn() == null),
                eq(owners.key(EMAIL)), any());
    }

    @Test
    void kaskoGosbIsPassedToWorkflow() throws Exception {
        when(workflows.startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION), any(), any(), any()))
                .thenReturn(new WorkflowGateway.StartedWithUpdate<>("kasko-1",
                        Optional.of(PreviewGate.of(PreviewGateOutcome.ACCEPTED))));
        mvc.perform(post("/api/v1/calculation/kasko").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content(KASKO.replace("\"regionCode\":77", "\"regionCode\":77,\"gosb\":\"38\"")))
                .andExpect(status().isAccepted());
        verify(workflows).startWithUpdate(eq(LkWorkflows.INITIATE_ACCOUNT_KASKO_CALCULATION),
                argThat((InitiateAccountKaskoCalculationInput input) -> "38".equals(input.request().insurant().gosb())),
                any(), any());
    }
}
