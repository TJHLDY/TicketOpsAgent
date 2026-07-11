package com.tzq.ticketops.agent;

import com.tzq.ticketops.agent.decision.AgentDecision;
import com.tzq.ticketops.agent.decision.AgentDecisionPort;
import com.tzq.ticketops.agent.decision.AgentMode;
import com.tzq.ticketops.agent.decision.LlmDecisionException;
import com.tzq.ticketops.agent.decision.PendingActionProposal;
import com.tzq.ticketops.agent.decision.ToolIntent;
import com.tzq.ticketops.observability.MicrometerAgentTelemetry;
import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import com.tzq.ticketops.tools.ReadOnlyToolExecutor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTelemetryTest {

    @Test
    void recordsSuccessfulPipelineAndAuditOnlyPendingAction() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOrchestrator orchestrator = createDeterministic(registry, 0.30);

        orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "OA 登录失败",
                "账号已锁定，请帮我核实。"
        ));

        assertThat(registry.find("ticketops.agent.request").timer()).isNull();
        assertCounter(registry, "ticketops.rag.retrieval", 1,
                "status", "accepted", "provider", "offline");
        assertCounter(registry, "ticketops.tool.execution", 1,
                "tool", "getAccountStatus", "outcome", "success", "reason", "none");
        assertCounter(registry, "ticketops.pending.action", 1,
                "type", "unlock_account");
    }

    @Test
    void recordsRagRejectionWithoutToolOrPendingMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentOrchestrator orchestrator = createDeterministic(registry, 1.0);

        orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "OA account locked",
                "My account is locked, with an intentionally strict threshold."
        ));

        assertThat(registry.find("ticketops.agent.request").timer()).isNull();
        assertCounter(registry, "ticketops.rag.retrieval", 1,
                "status", "low_similarity", "provider", "offline");
        assertThat(registry.find("ticketops.tool.execution").counter()).isNull();
        assertThat(registry.find("ticketops.pending.action").counter()).isNull();
    }

    @Test
    void recordsToolRejectionWithoutSuccessOrPendingMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentDecisionPort maliciousDecision = context -> new AgentDecision(
                TicketCategory.ACCOUNT_LOCKED,
                TicketPriority.P2,
                RiskLevel.NEEDS_APPROVAL,
                "账号锁定处理",
                List.of(new ToolIntent("getAccountStatus", Map.of("userId", "other-user"))),
                new PendingActionProposal(PendingActionType.UNLOCK_ACCOUNT, true),
                "",
                "",
                1.0,
                List.of("test")
        );
        AgentOrchestrator orchestrator = AgentOrchestrator.createWithPrimaryDecision(
                maliciousDecision,
                SopSearchService.createOffline(0.30),
                new ReadOnlyToolExecutor(new MockAccountStatusTool(), new MockUserPermissionsTool(), 1),
                new MicrometerAgentTelemetry(registry)
        );

        orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "OA 登录失败",
                "账号已锁定。"
        ));

        assertThat(registry.find("ticketops.agent.request").timer()).isNull();
        assertCounter(registry, "ticketops.tool.execution", 1,
                "tool", "getAccountStatus", "outcome", "rejected", "reason", "requester_mismatch");
        assertThat(registry.find("ticketops.pending.action").counter()).isNull();
    }

    @Test
    void recordsShadowFallbackWithoutChangingDeterministicOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentDecisionPort failingShadow = context -> {
            throw new LlmDecisionException("PARSE_ERROR", "invalid json");
        };
        AgentOrchestrator orchestrator = AgentOrchestrator.createWithDecisionMode(
                AgentMode.SHADOW,
                failingShadow,
                SopSearchService.createOffline(0.30),
                new MockAccountStatusTool(),
                new MockUserPermissionsTool(),
                new MicrometerAgentTelemetry(registry)
        );

        AgentResponse response = orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "OA 登录失败",
                "账号已锁定。"
        ));

        assertThat(response.category()).isEqualTo(TicketCategory.ACCOUNT_LOCKED);
        assertCounter(registry, "ticketops.shadow.decision", 1,
                "outcome", "fallback");
        assertThat(registry.find("ticketops.agent.request").timer()).isNull();
    }

    private AgentOrchestrator createDeterministic(SimpleMeterRegistry registry, double threshold) {
        return AgentOrchestrator.createWithDecisionMode(
                AgentMode.DETERMINISTIC,
                null,
                SopSearchService.createOffline(threshold),
                new MockAccountStatusTool(),
                new MockUserPermissionsTool(),
                new MicrometerAgentTelemetry(registry)
        );
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
