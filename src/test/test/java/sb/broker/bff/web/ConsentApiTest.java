package sb.broker.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.consent.ConsentFileOutcome;
import sb.broker.bff.workflow.api.consent.ConsentType;
import sb.broker.bff.workflow.api.consent.ReadConsentFileInput;
import sb.broker.bff.workflow.api.consent.ReadConsentFileResult;
import sb.broker.bff.workflow.api.consent.ReadUserConsentsResult;
import sb.broker.bff.workflow.api.consent.RecallUserConsentInput;
import sb.broker.bff.workflow.api.consent.SubmitUserConsentsInput;
import sb.broker.bff.workflow.api.consent.UserConsent;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConsentApiTest extends ApiTestSupport {

    @Test
    void consentsAreReturnedWithoutAbsentDate() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_USER_CONSENTS), any())).thenReturn(new ReadUserConsentsResult(
                AccessOutcome.GRANTED, List.of(
                new UserConsent("Поручение", "ASSIGNMENT", false, true, "assignment-guid", null),
                new UserConsent("Рассылка", "CSI", true, false, "csi-guid", "2026-10-26T12:00:00+03:00"))));

        mvc.perform(get("/api/v1/consents").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"title":"Поручение","type":"ASSIGNMENT","status":false,"required":true,"guid":"assignment-guid"},
                         {"title":"Рассылка","type":"CSI","status":true,"required":false,"guid":"csi-guid","date":"2026-10-26T12:00:00+03:00"}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @Test
    void consentsOfUnknownUserAre401() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_USER_CONSENTS), any()))
                .thenReturn(ReadUserConsentsResult.unauthorized());

        mvc.perform(get("/api/v1/consents").with(user()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fileIsReturnedWithCrmContentType() throws Exception {
        when(workflows.execute(LkWorkflows.READ_CONSENT_FILE, new ReadConsentFileInput(EMAIL, ConsentType.PDN)))
                .thenReturn(new ReadConsentFileResult(ConsentFileOutcome.GRANTED, new byte[]{1, 2, 3}, "application/pdf"));

        mvc.perform(get("/api/v1/consents/PDN/file").with(user()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
    }

    @Test
    void fileWithoutContentTypeIsOctetStream() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_CONSENT_FILE), any()))
                .thenReturn(new ReadConsentFileResult(ConsentFileOutcome.GRANTED, new byte[]{1}, null));

        mvc.perform(get("/api/v1/consents/CSI/file").with(user()))
                .andExpect(header().string("Content-Type", "application/octet-stream"));
    }

    @Test
    void missingFileIs404() throws Exception {
        when(workflows.execute(eq(LkWorkflows.READ_CONSENT_FILE), any()))
                .thenReturn(ReadConsentFileResult.of(ConsentFileOutcome.NOT_FOUND));

        mvc.perform(get("/api/v1/consents/CSI/file").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Файл согласия не найден"));
    }

    @Test
    void unknownConsentTypeIs400() throws Exception {
        mvc.perform(get("/api/v1/consents/OTHER/file").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректный тип согласия"));

        verifyNoInteractions(workflows);
    }

    @Test
    void submitSendsOnlyAcceptedConsents() throws Exception {
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/consents").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        [{"type":"PDN","consent":true},{"type":"CSI","consent":false},{"type":"ASSIGNMENT","consent":true}]
                        """))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(workflows).execute(LkWorkflows.SUBMIT_USER_CONSENTS,
                new SubmitUserConsentsInput(EMAIL, List.of(ConsentType.PDN, ConsentType.ASSIGNMENT)));
    }

    @Test
    void submitWithoutPdnIs400() throws Exception {
        mvc.perform(post("/api/v1/consents").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        [{"type":"PDN","consent":false}]
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Нет согласия на обработку персональных данных"));

        verifyNoInteractions(workflows);
    }

    @Test
    void submitWithDuplicateTypeIs400() throws Exception {
        mvc.perform(post("/api/v1/consents").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        [{"type":"PDN","consent":true},{"type":"PDN","consent":true}]
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflows);
    }

    @Test
    void submitWithIncompleteElementIs400() throws Exception {
        mvc.perform(post("/api/v1/consents").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        [{"type":"PDN"}]
                        """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(workflows);
    }

    @Test
    void submitIsAllowedWithoutRequiredConsent() throws Exception {
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/consents").with(user()).contentType(MediaType.APPLICATION_JSON).content("""
                        [{"type":"PDN","consent":true}]
                        """))
                .andExpect(status().isOk());

        verify(workflows).execute(LkWorkflows.CHECK_LK_ACCESS,
                new sb.broker.bff.workflow.api.access.CheckLkAccessInput(EMAIL, false));
    }

    @Test
    void recallRequiresConsentsAndDoesNotCallCrmWithoutThem() throws Exception {
        accessIs(AccessOutcome.CONSENTS_REQUIRED);

        mvc.perform(post("/api/v1/consents/CSI/recall").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("consents_required"));

        verify(workflows, never()).execute(eq(LkWorkflows.RECALL_USER_CONSENT), any());
    }

    @Test
    void recallIs200WithoutBody() throws Exception {
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/consents/CSI/recall").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        verify(workflows).execute(LkWorkflows.RECALL_USER_CONSENT, new RecallUserConsentInput(EMAIL, ConsentType.CSI));
    }
}
