package sb.broker.bff.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import sb.broker.bff.temporal.LkWorkflows;
import sb.broker.bff.temporal.WorkflowGateway;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;
import sb.broker.bff.workflow.api.access.CheckLkAccessResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * HTTP-слой целиком (security, валидация, ошибки, SSE) с подменой Temporal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestSupport {

    protected static final String EMAIL = "user@example.com";
    protected static final String INN = "7707083893";

    @Autowired
    protected MockMvc mvc;

    @MockitoBean
    protected WorkflowGateway workflows;

    protected static RequestPostProcessor user() {
        return jwt().jwt(token -> token.claim("email", EMAIL));
    }

    protected void accessIs(AccessOutcome outcome) {
        CheckLkAccessResult result = outcome == AccessOutcome.UNAUTHORIZED
                ? CheckLkAccessResult.unauthorized()
                : new CheckLkAccessResult(outcome, INN, EMAIL);
        when(workflows.execute(eq(LkWorkflows.CHECK_LK_ACCESS), any(CheckLkAccessInput.class))).thenReturn(result);
    }
}
