package com.tzq.ticketops.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ticketops.agent.mode=shadow")
class AgentOrchestratorModeConfigurationTest {

    @Autowired
    AgentOrchestrator orchestrator;

    @Test
    void configuredShadowModeRecordsSkippedShadowDecisionWhenNoLlmPortExists() {
        AgentResponse response = orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "unknown issue",
                "unknown issue"
        ));

        assertThat(response.traceEvents()).extracting(TraceEvent::step)
                .contains("LLM_SHADOW_SKIPPED");
        assertThat(response.traceEvents()).extracting(TraceEvent::detail)
                .anySatisfy(detail -> assertThat(detail)
                        .contains("agentMode=SHADOW")
                        .contains("reason=no_shadow_decision_port"));
    }
}
