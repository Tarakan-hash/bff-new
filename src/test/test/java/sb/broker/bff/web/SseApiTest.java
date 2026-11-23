package sb.broker.bff.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowExecutionException;
import sb.broker.bff.temporal.WorkflowNotFoundException;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfile;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfileErrorCode;
import sb.broker.bff.workflow.api.companyprofile.CompanyProfileResult;
import sb.broker.bff.workflow.api.policy.PoliciesOutcome;
import sb.broker.bff.workflow.api.policy.SyncPoliciesResult;
import sb.broker.bff.workflow.api.riskprofile.RiskProfileResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SseApiTest extends ApiTestSupport {

    private static final String SESSION = "lk-company-profile-" + INN + "-1";

    @Test
    void companyProfileInitiateReturnsWorkflowId() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.start(eq(LkWorkflows.INITIATE_COMPANY_PROFILE), any(), eq(INN))).thenReturn(SESSION);

        mvc.perform(post("/api/v1/company-profile").with(user()))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"workflowId\":\"" + SESSION + "\"}"));
    }

    @Test
    void companyProfileCompleteEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        sessionOwned(true);
        when(workflows.awaitResult(LkWorkflows.INITIATE_COMPANY_PROFILE, SESSION)).thenReturn(CompanyProfileResult.of(
                new CompanyProfile(INN, "ООО «Пример»", null, null, null, null, null, 76, null, null, null)));

        assertThat(stream("/api/v1/company-profile/" + SESSION))
                .startsWith("event:complete\ndata:{\"inn\":\"7707083893\",\"name\":\"ООО «Пример»\"")
                .contains("\"regionCode\":76", "\"additionalOkveds\":null");
    }

    @Test
    void companyProfileErrorEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        sessionOwned(true);
        when(workflows.awaitResult(LkWorkflows.INITIATE_COMPANY_PROFILE, SESSION)).thenReturn(CompanyProfileResult.of(
                CompanyProfileErrorCode.COMPANY_NOT_FOUND));

        assertThat(stream("/api/v1/company-profile/" + SESSION)).isEqualTo("""
                event:error
                data:{"code":"COMPANY_NOT_FOUND","message":"Компания с указанным ИНН не найдена в системе CRM."}

                """);
    }

    @Test
    void foreignSessionLooksLikeMissingOne() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        sessionOwned(false);

        assertThat(stream("/api/v1/company-profile/" + SESSION))
                .contains("\"code\":\"SESSION_NOT_FOUND\"", "Сессия профиля не найдена");
        verify(workflows, never()).awaitResult(any(), any());
    }

    @Test
    void missingSessionIsSessionNotFound() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        sessionOwned(true);
        when(workflows.awaitResult(LkWorkflows.INITIATE_COMPANY_PROFILE, SESSION))
                .thenThrow(new WorkflowNotFoundException(SESSION, null));

        assertThat(stream("/api/v1/company-profile/" + SESSION)).contains("SESSION_NOT_FOUND");
    }

    @Test
    void failedSessionIsInternalFallback() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        sessionOwned(true);
        when(workflows.awaitResult(LkWorkflows.INITIATE_COMPANY_PROFILE, SESSION))
                .thenThrow(new WorkflowExecutionException("failed", null));

        assertThat(stream("/api/v1/company-profile/" + SESSION))
                .contains("INTERNAL_FALLBACK", "Внутренняя ошибка сервера при агрегации данных.");
    }

    @Test
    void consentsRequiredIsJsonBeforeStream() throws Exception {
        accessIs(AccessOutcome.CONSENTS_REQUIRED);

        mvc.perform(get("/api/v1/company-profile/" + SESSION).with(user()).accept("text/event-stream"))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"outcome":"consents_required","message":"Требуется согласие"}"""));
    }

    @Test
    void riskProfileOutcomeMessageIsErrorEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.isOwnedBy(LkWorkflows.INITIATE_RISK_PROFILE, "risk-1", INN)).thenReturn(true);
        when(workflows.awaitResult(LkWorkflows.INITIATE_RISK_PROFILE, "risk-1"))
                .thenReturn(RiskProfileResult.failure(404, "Нет риск-профиля для отображения"));

        assertThat(stream("/api/v1/risk-profile-recommendations/risk-1")).isEqualTo("""
                event:error
                data:{"message":"Нет риск-профиля для отображения"}

                """);
    }

    @Test
    void policiesCompleteEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.startOrAttach(eq(LkWorkflows.SYNC_POLICIES), any(), eq(INN))).thenReturn("sync");
        when(workflows.awaitResult(LkWorkflows.SYNC_POLICIES, "sync"))
                .thenReturn(new SyncPoliciesResult(PoliciesOutcome.COMPLETE, List.of()));

        assertThat(stream("/api/v1/policies")).isEqualTo("event:complete\ndata:[]\n\n");
    }

    @Test
    void policiesWithoutDataIsErrorEvent() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.startOrAttach(eq(LkWorkflows.SYNC_POLICIES), any(), eq(INN))).thenReturn("sync");
        when(workflows.awaitResult(LkWorkflows.SYNC_POLICIES, "sync"))
                .thenReturn(new SyncPoliciesResult(PoliciesOutcome.NO_POLICIES, List.of()));

        assertThat(stream("/api/v1/policies")).contains("event:error", "Нет полисов для отображения");
    }

    private void sessionOwned(boolean owned) {
        when(workflows.isOwnedBy(LkWorkflows.INITIATE_COMPANY_PROFILE, SESSION, INN)).thenReturn(owned);
    }

    private String stream(String path) throws Exception {
        MvcResult started = mvc.perform(get(path).with(user()).accept("text/event-stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);
        return mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void blankWorkflowIdIs400BeforeAccess() throws Exception {
        mvc.perform(get("/api/v1/company-profile/ ").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Идентификатор операции не передан\"}"));
        mvc.perform(get("/api/v1/risk-profile-recommendations/").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"message\":\"Идентификатор операции не передан\"}"));

        org.mockito.Mockito.verifyNoInteractions(workflows);
    }

    @Test
    @Disabled
    void sseWithoutTokenIs401Json() throws Exception {
        mvc.perform(get("/api/v1/policies").accept("text/event-stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"message\":\"Нет доступа\"}"));
    }

    @Test
    void policiesSyncStartFailureIs500JsonBeforeStream() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.startOrAttach(eq(LkWorkflows.SYNC_POLICIES), any(), eq(INN)))
                .thenThrow(new WorkflowExecutionException("Temporal is down", null));

        mvc.perform(get("/api/v1/policies").with(user()).accept("text/event-stream"))
                .andExpect(request().asyncNotStarted())
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"message\":\"Внутренняя ошибка сервиса\"}"));
    }

    @Test
    void riskInitiateAttachesToRunningCalculationOfInn() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.startOrAttach(eq(LkWorkflows.INITIATE_RISK_PROFILE), any(), eq(INN)))
                .thenReturn("lk-risk-profile-" + INN);

        mvc.perform(post("/api/v1/risk-profile-recommendations").with(user()))
                .andExpect(status().isAccepted())
                .andExpect(content().json("{\"workflowId\":\"lk-risk-profile-" + INN + "\"}"));
        mvc.perform(post("/api/v1/risk-profile-recommendations").with(user()))
                .andExpect(content().json("{\"workflowId\":\"lk-risk-profile-" + INN + "\"}"));

        verify(workflows, never()).start(eq(LkWorkflows.INITIATE_RISK_PROFILE), any(), any());
    }
}
