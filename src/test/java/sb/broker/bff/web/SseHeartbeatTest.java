package sb.broker.bff.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import sb.broker.bff.policy.dto.SyncOrgPoliciesWorkflowResult;
import sb.broker.bff.support.ApiTestSupport;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.policy.PoliciesOutcome;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Пока результат считается, поток не молчит: ingress не рвёт его по read timeout.
 */
@TestPropertySource(properties = "bff.sse.heartbeat=50ms")
class SseHeartbeatTest extends ApiTestSupport {

    @Test
    void slowResultIsPrecededByHeartbeatComments() throws Exception {
        accessIs(AccessOutcome.GRANTED);
        when(workflows.startOrAttach(eq(LkWorkflows.SYNC_POLICIES), any(), eq(INN))).thenReturn("sync");
        when(workflows.awaitResult(LkWorkflows.SYNC_POLICIES, "sync")).thenAnswer(invocation -> {
            Thread.sleep(300);
            return new SyncOrgPoliciesWorkflowResult(PoliciesOutcome.COMPLETE, List.of());
        });

        MvcResult started = mvc.perform(get("/api/v1/policies").with(user()).accept("text/event-stream"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(5_000);
        String body = mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).startsWith(":keep-alive\n\n").endsWith("event:complete\ndata:[]\n\n");
    }
}
