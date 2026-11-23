package sb.broker.bff.web;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import sb.broker.bff.support.ApiTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Пробы k8s: живость не зависит от Temporal (его сбой не перезапускает поды), готовность — зависит.
 * В тестах Temporal недоступен.
 */
class OperationsTest extends ApiTestSupport {

    @Test
    @Disabled
    void livenessDoesNotDependOnTemporal() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Disabled
    void readinessIsDownWithoutTemporal() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }
}
