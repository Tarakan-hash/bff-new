package sb.broker.bff.policy.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import sb.broker.bff.workflow.api.policy.PoliciesOutcome;


import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SyncOrgPoliciesWorkflowResult(
        PoliciesOutcome outcome,
        List<Policy> policies
) {
    public SyncOrgPoliciesWorkflowResult {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }
}