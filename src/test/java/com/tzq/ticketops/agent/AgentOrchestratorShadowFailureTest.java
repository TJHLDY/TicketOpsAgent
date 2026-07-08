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
                        .contains("fallback_to=DETERMINISTIC"));
    }
}
