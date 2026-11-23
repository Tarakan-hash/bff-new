package sb.broker.bff.policy;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import sb.broker.bff.policy.dto.SyncOrgPoliciesWorkflowResult;
import sb.broker.bff.workflow.api.policy.SyncPoliciesInput;

@WorkflowInterface
public interface SyncOrgPoliciesWorkflow {
    @WorkflowMethod
    SyncOrgPoliciesWorkflowResult sync(SyncPoliciesInput input);
}
