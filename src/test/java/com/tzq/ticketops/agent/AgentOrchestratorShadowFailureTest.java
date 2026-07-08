package com.tzq.ticketops.agent;

import com.tzq.ticketops.agent.decision.AgentContext;
import com.tzq.ticketops.agent.decision.AgentDecision;
import com.tzq.ticketops.agent.decision.AgentDecisionPort;
import com.tzq.ticketops.agent.decision.AgentMode;
import com.tzq.ticketops.agent.decision.LlmDecisionException;
import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorShadowFailureTest {

    @Test
    void shadowFailureKeepsDeterministicResultAndRecordsFallback() {
        AgentDecisionPort failingShadowPort = new AgentDecisionPort() {
            @Override
            public AgentDecision decide(AgentContext context) {
                throw new LlmDecisionException("PARSE_ERROR", "invalid json");
            }
        };
        AgentOrchestrator orchestrator = AgentOrchestrator.createWithDecisionMode(
                AgentMode.SHADOW,
                failingShadowPort,
                new SopSearchService(),
                new MockAccountStatusTool(),
                new MockUserPermissionsTool()
        );

        AgentResponse response = orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "unknown issue",
                "unknown issue"
        ));

        assertThat(response.category()).isEqualTo(TicketCategory.UNKNOWN);
        assertThat(response.traceEvents()).extracting(TraceEvent::step)
                .contains("LLM_SHADOW_FAILED");
        assertThat(response.traceEvents()).extracting(TraceEvent::detail)
                .anySatisfy(detail -> assertThat(detail)
                        .contains("llm_status=PARSE_ERROR")
                        .contains("fallback_reason=PARSE_ERROR")
                        .contains("fallback_to=DETERMINISTIC")
                        .contains("provider=deepseek")
                        .contains("model=deepseek-v4-flash")
                        .contains("prompt_version=deepseek-shadow-v2")
                        .contains("schema_version=agent-decision-v1")
                        .contains("final_decision_source=DETERMINISTIC")
                        .contains("user_visible_changed=false")
                        .contains("validation_errors=PARSE_ERROR")
                        .contains("latency_ms="));
    }
}
