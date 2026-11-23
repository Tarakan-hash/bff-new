package sb.broker.bff.temporal;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.temporal.client.WorkflowClient;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sb.broker.bff.calculation.dms.dto.AccountDmsCalculationWorkflowInput;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties.Defaults;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties.Overrides;
import sb.broker.bff.config.properties.TemporalWorkflowsProperties;
import sb.broker.bff.policy.SyncOrgPoliciesWorkflow;
import sb.broker.bff.policy.dto.SyncOrgPoliciesWorkflowResult;
import sb.broker.bff.workflow.api.access.AccessOutcome;
import sb.broker.bff.workflow.api.access.CheckLkAccessInput;
import sb.broker.bff.workflow.api.access.CheckLkAccessResult;
import sb.broker.bff.workflow.api.access.CheckLkAccessWorkflow;
import sb.broker.bff.workflow.api.calculation.DmsPreviewResult;
import sb.broker.bff.workflow.api.calculation.InitiateAccountDmsCalculationInput;
import sb.broker.bff.workflow.api.calculation.InitiateAccountDmsCalculationWorkflow;
import sb.broker.bff.workflow.api.calculation.OfferSelection;
import sb.broker.bff.workflow.api.calculation.OfferSelectionOutcome;
import sb.broker.bff.workflow.api.calculation.OwnerRequest;
import sb.broker.bff.workflow.api.calculation.PreviewGate;
import sb.broker.bff.workflow.api.calculation.PreviewGateOutcome;
import sb.broker.bff.workflow.api.calculation.SelectOfferRequest;
import sb.broker.bff.workflow.api.policy.PoliciesOutcome;
import sb.broker.bff.workflow.api.policy.SyncPoliciesInput;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowGatewayTest {

    private static final String TASK_QUEUE = "test-tq";
    private static final AtomicInteger SYNC_RUNS = new AtomicInteger();

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private TestWorkflowEnvironment environment;
    private WorkflowGateway gateway;

    /** Допуск по email: известный — granted, "slow" — долго, "broken" — ошибка. */
    public static class FakeCheckLkAccessWorkflow implements CheckLkAccessWorkflow {
        @Override
        public CheckLkAccessResult check(CheckLkAccessInput input) {
            return switch (input.email()) {
                case "slow@example.com" -> {
                    Workflow.sleep(Duration.ofMinutes(5));
                    yield CheckLkAccessResult.unauthorized();
                }
                case "broken@example.com" -> throw ApplicationFailure.newNonRetryableFailure("boom", "Test");
                default -> new CheckLkAccessResult(AccessOutcome.GRANTED, "7707083893", input.email());
            };
        }
    }

    public static class FakeSyncPoliciesWorkflow implements SyncOrgPoliciesWorkflow {
        @Override
        public SyncOrgPoliciesWorkflowResult sync(SyncPoliciesInput input) {
            SYNC_RUNS.incrementAndGet();
            Workflow.sleep(Duration.ofSeconds(10));
            return new SyncOrgPoliciesWorkflowResult(PoliciesOutcome.COMPLETE, List.of());
        }
    }

    /**
     * Предрасчёт: «ворота» сразу, предложения через 1 секунду, выбор — только {@code ok}. Email {@code fast@…} —
     * workflow закрывается после предложений.
     */
    public static class FakeDmsCalculationWorkflow implements InitiateAccountDmsCalculationWorkflow {

        private DmsPreviewResult preview;

        @Override
        public DmsPreviewResult calculate(InitiateAccountDmsCalculationInput input) {
            Workflow.sleep(Duration.ofSeconds(1));
            preview = DmsPreviewResult.refused(PreviewGateOutcome.ACCEPTED);
            if (!input.email().startsWith("fast")) {
                Workflow.sleep(Duration.ofHours(24));
            }
            return preview;
        }

        @Override
        public PreviewGate awaitGate(OwnerRequest request) {
            return PreviewGate.of(PreviewGateOutcome.ACCEPTED);
        }

        @Override
        public void validateAwaitGate(OwnerRequest request) {
        }

        @Override
        public DmsPreviewResult awaitPreview(OwnerRequest request) {
            Workflow.await(() -> preview != null);
            return preview;
        }

        @Override
        public void validateAwaitPreview(OwnerRequest request) {
        }

        @Override
        public OfferSelection selectOffer(SelectOfferRequest request) {
            if ("boom".equals(request.offerId())) {
                throw ApplicationFailure.newNonRetryableFailure("boom", "Test");
            }
            return new OfferSelection(request.offerId(), OfferSelectionOutcome.ACCEPTED);
        }

        @Override
        public void validateSelectOffer(SelectOfferRequest request) {
            if ("bad".equals(request.offerId())) {
                throw ApplicationFailure.newNonRetryableFailure("OFFER_NOT_FOUND", "OFFER_NOT_FOUND");
            }
        }
    }

    @BeforeEach
    void setUp() {
        SYNC_RUNS.set(0);
        environment = TestWorkflowEnvironment.newInstance();
        Worker worker = environment.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(FakeCheckLkAccessWorkflow.class, FakeSyncPoliciesWorkflow.class,
                FakeDmsCalculationWorkflow.class);
        environment.start();
        WorkflowClient client = environment.getWorkflowClient();
        TemporalWorkflowsProperties properties = new TemporalWorkflowsProperties(
                new Defaults(TASK_QUEUE, Duration.ofHours(1), Duration.ofSeconds(30)),
                Map.of("SyncOrgPoliciesWorkflow", new Overrides(null, "policy-sync-", null, Duration.ofMinutes(1)),
                        "InitiateAccountDmsCalculationWorkflow", new Overrides(null, "lk-calc-dms-", Duration.ofHours(26),
                                Duration.ofSeconds(5))),
                Duration.ofSeconds(2));
        gateway = new WorkflowGateway(client, properties, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        gateway.stopDeadlines();
        environment.close();
    }

    @Test
    void updateDeadlineIncludesAcceptanceWithoutWorker() throws Exception {
        var properties = new TemporalWorkflowsProperties(
                new Defaults("no-worker", Duration.ofMinutes(5), Duration.ofMillis(100)), Map.of(), Duration.ofSeconds(1));
        var bounded = new WorkflowGateway(environment.getWorkflowClient(), properties, meterRegistry);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var input = new AccountDmsCalculationWorkflowInput("user@example.com", "request", null);
            var call = new UpdateCall<>("awaitGate", new OwnerRequest(input.email()), PreviewGate.class);
            var started = executor.submit(() -> bounded.startWithUpdate(
                            LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, input, "owner", call))
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(started.result()).isEmpty();
            assertThat(executor.submit(() -> bounded.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION,
                            started.workflowId(), call, Duration.ofMillis(100)))
                    .get(2, java.util.concurrent.TimeUnit.SECONDS)).isEmpty();
        } finally {
            bounded.stopDeadlines();
        }
    }

    @Test
    void executeReturnsWorkflowResult() {
        CheckLkAccessResult result = gateway.execute(LkWorkflows.CHECK_LK_ACCESS,
                new CheckLkAccessInput("user@example.com", true));

        assertThat(result).isEqualTo(new CheckLkAccessResult(AccessOutcome.GRANTED, "7707083893", "user@example.com"));
        assertThat(meterRegistry.get("bff.workflow").tags("workflow", "CheckLkAccessWorkflow",
                "operation", "await", "outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void failedWorkflowIsReportedAsExecutionFailure() {
        assertThatThrownBy(() -> gateway.execute(LkWorkflows.CHECK_LK_ACCESS,
                new CheckLkAccessInput("broken@example.com", true)))
                .isExactlyInstanceOf(WorkflowExecutionException.class);
        assertThat(meterRegistry.get("bff.workflow").tags("outcome", "failed").timer().count()).isEqualTo(1);
    }

    @Test
    void unknownWorkflowIdIsReportedAsNotFound() {
        assertThatThrownBy(() -> gateway.awaitResult(LkWorkflows.CHECK_LK_ACCESS, "missing-workflow"))
                .isInstanceOf(WorkflowNotFoundException.class);
    }

    @Test
    void workflowIdCarriesOwnerKey() {
        String workflowId = gateway.start(LkWorkflows.CHECK_LK_ACCESS,
                new CheckLkAccessInput("user@example.com", false), "7707083893");

        assertThat(workflowId).startsWith("CheckLkAccessWorkflow-7707083893-");
        assertThat(gateway.isOwnedBy(LkWorkflows.CHECK_LK_ACCESS, workflowId, "7707083893")).isTrue();
        assertThat(gateway.isOwnedBy(LkWorkflows.CHECK_LK_ACCESS, workflowId, "770708389")).isFalse();
    }

    @Test
    void startOrAttachReusesRunningWorkflow() {
        SyncPoliciesInput input = new SyncPoliciesInput("7707083893");

        String first = gateway.startOrAttach(LkWorkflows.SYNC_POLICIES, input, "7707083893");
        String second = gateway.startOrAttach(LkWorkflows.SYNC_POLICIES, input, "7707083893");
        SyncOrgPoliciesWorkflowResult result = gateway.awaitResult(LkWorkflows.SYNC_POLICIES, second);

        assertThat(first).isEqualTo(second).isEqualTo("policy-sync-7707083893");
        assertThat(gateway.isOwnedBy(LkWorkflows.SYNC_POLICIES, first, "7707083893")).isTrue();
        assertThat(gateway.isOwnedBy(LkWorkflows.SYNC_POLICIES, first, "770708389")).isFalse();
        assertThat(result.outcome()).isEqualTo(PoliciesOutcome.COMPLETE);
        assertThat(SYNC_RUNS).hasValue(1);
    }

    // ===== Update =====

    @Test
    void startWithUpdateReturnsGateAndOwnedWorkflowId() {
        WorkflowGateway.StartedWithUpdate<PreviewGate> started = startCalculation("user@example.com");

        assertThat(started.result()).contains(PreviewGate.of(PreviewGateOutcome.ACCEPTED));
        assertThat(gateway.isOwnedBy(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, started.workflowId(), "owner-key")).isTrue();
    }

    @Test
    void updateWaitsForPreviewAndFailuresAreUnavailable() {
        String workflowId = startCalculation("user@example.com").workflowId();

        assertThat(gateway.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                new UpdateCall<>("awaitPreview", new OwnerRequest("user@example.com"), DmsPreviewResult.class),
                Duration.ofSeconds(30))).isPresent();
        assertThat(gateway.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                new UpdateCall<>("selectOffer", new SelectOfferRequest("user@example.com", "ok"), OfferSelection.class),
                Duration.ofSeconds(30))).map(OfferSelection::outcome).contains(OfferSelectionOutcome.ACCEPTED);
        assertThatThrownBy(() -> gateway.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                new UpdateCall<>("selectOffer", new SelectOfferRequest("user@example.com", "bad"), OfferSelection.class),
                Duration.ofSeconds(30)))
                .isInstanceOf(WorkflowUpdateRejectedException.class);
        assertThatThrownBy(() -> gateway.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                new UpdateCall<>("selectOffer", new SelectOfferRequest("user@example.com", "boom"), OfferSelection.class),
                Duration.ofSeconds(30)))
                .as("сбой обработчика от отказа валидатора клиенту не отличить")
                .isInstanceOf(WorkflowUpdateRejectedException.class);
    }

    @Test
    void updateOfClosedWorkflowIsNotFoundAndResultIsReadable() {
        String workflowId = startCalculation("fast@example.com").workflowId();
        environment.sleep(Duration.ofSeconds(5));

        assertThatThrownBy(() -> gateway.update(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId,
                new UpdateCall<>("awaitPreview", new OwnerRequest("fast@example.com"), DmsPreviewResult.class),
                Duration.ofSeconds(5)))
                .isInstanceOf(WorkflowNotFoundException.class);
        assertThat(gateway.closedResult(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, workflowId)).isPresent();
        assertThatThrownBy(() -> gateway.closedResult(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION, "lk-calc-dms-missing"))
                .isInstanceOf(WorkflowNotFoundException.class);
    }

    private WorkflowGateway.StartedWithUpdate<PreviewGate> startCalculation(String email) {
        return gateway.startWithUpdate(LkWorkflows.INITIATE_ACCOUNT_DMS_CALCULATION,
                new AccountDmsCalculationWorkflowInput(email, "00000000-0000-0000-0000-000000000001", null), "owner-key",
                new UpdateCall<>("awaitGate", new OwnerRequest(email), PreviewGate.class));
    }
}
