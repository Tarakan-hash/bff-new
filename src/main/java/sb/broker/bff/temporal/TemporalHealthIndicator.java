package sb.broker.bff.temporal;

import io.temporal.api.workflowservice.v1.DescribeNamespaceRequest;
import io.temporal.client.WorkflowClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties;

import java.util.concurrent.TimeUnit;

/**
 * Готовность пода: без Temporal bff не обслуживает ни один метод кабинета.
 * Входит в группу {@code readiness}; liveness от Temporal не зависит, чтобы сбой Temporal не перезапускал поды.
 */
@Slf4j
@Component("temporal")
public class TemporalHealthIndicator extends AbstractHealthIndicator {

    private final WorkflowClient client;
    private final TemporalWorkflowsProperties properties;

    public TemporalHealthIndicator(WorkflowClient client, TemporalWorkflowsProperties properties) {
        super("Temporal is unavailable");
        this.client = client;
        this.properties = properties;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        String namespace = client.getOptions().getNamespace();
        log.debug("Temporal health check: namespace={}", namespace);
        client.getWorkflowServiceStubs().blockingStub()
                .withDeadlineAfter(properties.healthTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .describeNamespace(DescribeNamespaceRequest.newBuilder().setNamespace(namespace).build());
        builder.up().withDetail("namespace", namespace);
    }
}
