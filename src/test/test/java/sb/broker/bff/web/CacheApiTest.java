package sb.broker.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;
import sb.broker.bff.workflow.api.catalog.CatalogOutcome;
import sb.broker.bff.workflow.api.catalog.ReadProductCatalogResult;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"bff.cache.access.ttl=1m", "bff.cache.catalog.ttl=1m"})
class CacheApiTest extends ApiTestSupport {

    @Test
    void grantedAccessIsCheckedOncePerSessionAndCoversWeakerPolicy() throws Exception {
        var user = jwt().jwt(token -> token.claim("email", "cached@example.com"));
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/policies").with(user)).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/policies").with(user)).andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/consents").with(user).contentType("application/json")
                .content("[{\"type\":\"PDN\",\"consent\":true}]")).andExpect(status().isOk());

        verify(workflows, times(1)).execute(eq(LkWorkflows.CHECK_LK_ACCESS), any());
    }

    @Test
    void refusalIsNotCached() throws Exception {
        var user = jwt().jwt(token -> token.claim("email", "refused@example.com"));
        accessIs(AccessOutcome.CONSENTS_REQUIRED);

        mvc.perform(post("/api/v1/policies").with(user)).andExpect(jsonPath("$.outcome").value("consents_required"));
        accessIs(AccessOutcome.GRANTED);
        mvc.perform(post("/api/v1/policies").with(user)).andExpect(status().isAccepted());

        verify(workflows, times(2)).execute(eq(LkWorkflows.CHECK_LK_ACCESS), any());
    }

    @Test
    void recallEvictsAccess() throws Exception {
        var user = jwt().jwt(token -> token.claim("email", "recall@example.com"));
        accessIs(AccessOutcome.GRANTED);

        mvc.perform(post("/api/v1/consents/PDN/recall").with(user)).andExpect(status().isOk());
        accessIs(AccessOutcome.CONSENTS_REQUIRED);
        mvc.perform(post("/api/v1/policies").with(user)).andExpect(jsonPath("$.outcome").value("consents_required"));

        verify(workflows, times(2)).execute(eq(LkWorkflows.CHECK_LK_ACCESS),
                eq(new CheckLkAccessInput("recall@example.com", true)));
    }

    @Test
    void cachedDictionaryStillRequiresAccess() throws Exception {
        var user = jwt().jwt(token -> token.claim("email", "catalog@example.com"));
        when(workflows.execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any()))
                .thenReturn(new ReadProductCatalogResult(CatalogOutcome.FOUND, List.of("77")));
        accessIs(AccessOutcome.UNAUTHORIZED);

        mvc.perform(get("/api/v1/age_group").with(user)).andExpect(status().isOk());
        // справочник в кэше, но пользователь без допуска его не получит
        mvc.perform(get("/api/v1/age_group").with(user)).andExpect(status().isUnauthorized());
        accessIs(AccessOutcome.GRANTED);
        mvc.perform(get("/api/v1/age_group").with(user)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/age_group").with(user)).andExpect(status().isOk());

        verify(workflows, times(1)).execute(eq(LkWorkflows.READ_PRODUCT_CATALOG), any());
        verify(workflows, times(2)).execute(eq(LkWorkflows.CHECK_LK_ACCESS), any());
    }
}
