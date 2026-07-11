package com.tzq.ticketops.observability;

import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.rag.SopSearchStatus;
import com.tzq.ticketops.tools.ReadOnlyToolExecutor;
import com.tzq.ticketops.tools.ToolRejectionReason;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerAgentTelemetryTest {

    @Test
    void recordsCompletedRequestDurationWithBoundedDomainTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new MicrometerAgentTelemetry(registry);

        AgentTelemetry.RequestObservation observation = telemetry.startRequest();
        observation.complete(
                AgentRequestOutcome.COMPLETED,
                TicketCategory.ACCOUNT_LOCKED,
                RiskLevel.NEEDS_APPROVAL
        );
        observation.complete(
                AgentRequestOutcome.ERROR,
                TicketCategory.UNKNOWN,
                RiskLevel.REJECT
        );

        assertThat(registry.get("ticketops.agent.request")
                .tags(
                        "outcome", "completed",
                        "category", "account_locked",
                        "risk", "needs_approval"
                )
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.get("ticketops.agent.request").timers()).hasSize(1);
    }

    @Test
    void recordsRagToolPendingActionAndShadowOutcomes() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new MicrometerAgentTelemetry(registry);

        telemetry.recordRag(SopSearchStatus.ACCEPTED, "offline");
        telemetry.recordRag(SopSearchStatus.LOW_SIMILARITY, "onnx");
        telemetry.recordToolSuccess(ReadOnlyToolExecutor.GET_ACCOUNT_STATUS);
        telemetry.recordToolRejected(
                ReadOnlyToolExecutor.GET_USER_PERMISSIONS,
                ToolRejectionReason.REQUESTER_MISMATCH
        );
        telemetry.recordPendingAction(PendingActionType.UNLOCK_ACCOUNT);
        telemetry.recordShadow(ShadowDecisionOutcome.FALLBACK);

        assertCounter(registry, "ticketops.rag.retrieval", 1,
                "status", "accepted", "provider", "offline");
        assertCounter(registry, "ticketops.rag.retrieval", 1,
                "status", "low_similarity", "provider", "onnx");
        assertCounter(registry, "ticketops.tool.execution", 1,
                "tool", "getAccountStatus", "outcome", "success", "reason", "none");
        assertCounter(registry, "ticketops.tool.execution", 1,
                "tool", "getUserPermissions", "outcome", "rejected", "reason", "requester_mismatch");
        assertCounter(registry, "ticketops.pending.action", 1,
                "type", "unlock_account");
        assertCounter(registry, "ticketops.shadow.decision", 1,
                "outcome", "fallback");
    }

    @Test
    void collapsesUntrustedValuesAndNeverPublishesSensitiveMarkers() {
        String sensitiveMarker = "requester-secret-9384";
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentTelemetry telemetry = new MicrometerAgentTelemetry(registry);

        telemetry.recordRag(SopSearchStatus.ACCEPTED, sensitiveMarker);
        telemetry.recordToolRejected(sensitiveMarker, ToolRejectionReason.UNAUTHORIZED_TOOL);

        List<String> meterMetadata = registry.getMeters().stream()
                .map(Meter::getId)
                .map(id -> id.getName() + "|" + id.getTags() + "|"
                        + id.getDescription() + "|" + id.getBaseUnit())
                .toList();

        assertThat(meterMetadata).noneMatch(value -> value.contains(sensitiveMarker));
        assertCounter(registry, "ticketops.rag.retrieval", 1,
                "status", "accepted", "provider", "other");
        assertCounter(registry, "ticketops.tool.execution", 1,
                "tool", "other", "outcome", "rejected", "reason", "unauthorized_tool");
    }

    private void assertCounter(
            SimpleMeterRegistry registry,
            String name,
            double expected,
            String... tags
    ) {
        assertThat(registry.get(name).tags(tags).counter().count()).isEqualTo(expected);
    }
}
