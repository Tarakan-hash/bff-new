package sb.broker.bff.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowExecutionException;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Общие ответы допуска на примере {@code POST /policies}.
 */
class AccessApiTest extends ApiTestSupport {

    @Test
    @Disabled
    void missingTokenIs401WithoutTemporal() throws Exception {
        mvc.perform(post("/api/v1/policies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Нет доступа"));

        verifyNoInteractions(workflows);
    }

    @Test
    void tokenWithoutEmailIs401() throws Exception {
        mvc.perform(post("/api/v1/policies").with(jwt()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Нет доступа"));
    }

    @Test
    void invalidEmailClaimIs401WithoutTemporal() throws Exception {
        for (Object email : java.util.List.of("", " ", 42, java.util.List.of("user@example.com"))) {
            mvc.perform(post("/api/v1/policies").with(jwt().jwt(token -> token.claim("email", email))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Нет доступа"));
        }
        verifyNoInteractions(workflows);
    }

    @Test
    void unknownLkUserIs401() throws Exception {
        accessIs(AccessOutcome.UNAUTHORIZED);

        mvc.perform(post("/api/v1/policies").with(user()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Нет доступа"));
    }

    @Test
    void missingRequiredConsentIs200WithOutcome() throws Exception {
        accessIs(AccessOutcome.CONSENTS_REQUIRED);

        mvc.perform(post("/api/v1/policies").with(user()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"outcome":"consents_required","message":"Требуется согласие"}"""));
    }

    @Test
    void accessWorkflowFailureIs500() throws Exception {
        when(workflows.execute(eq(LkWorkflows.CHECK_LK_ACCESS), any()))
                .thenThrow(new WorkflowExecutionException("failed", null));

        mvc.perform(post("/api/v1/policies").with(user()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Внутренняя ошибка сервиса"));
    }

    @Test
    void policiesImportStartsSingletonSyncForInn() throws Exception {
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/policies").with(user()))
                .andExpect(status().isAccepted())
                .andExpect(content().json("""
                        {"status":"accepted"}"""));

        verify(workflows).execute(LkWorkflows.CHECK_LK_ACCESS, new CheckLkAccessInput(EMAIL, true));
        verify(workflows).startOrAttach(eq(LkWorkflows.SYNC_POLICIES), any(), eq(INN));
    }
}
