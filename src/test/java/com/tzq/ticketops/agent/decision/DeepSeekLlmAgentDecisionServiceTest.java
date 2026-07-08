package com.tzq.ticketops.agent.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepSeekLlmAgentDecisionServiceTest {

    @Test
    void callsGatewayAndParsesReturnedDecision() {
        DeepSeekLlmAgentDecisionService service = new DeepSeekLlmAgentDecisionService(
                fixedProvider(prompt -> """
                        {
                          "category": "LOGIN_FAILED",
                          "priority": "P3",
                          "riskLevel": "READ_ONLY",
                          "confidence": 0.74,
                          "toolIntents": [],
                          "pendingActions": [],
                          "reasonCode": "LOGIN_FAILED_SIGNAL"
                        }
                        """),
                new LlmAgentDecisionParser(new ObjectMapper())
        );

        AgentDecision decision = service.decide(new AgentContext(
                "mock-user-001",
                "OA login failed",
                "The page says password is wrong."
        ));

        assertThat(decision.category()).isEqualTo(TicketCategory.LOGIN_FAILED);
        assertThat(decision.riskLevel()).isEqualTo(RiskLevel.READ_ONLY);
        assertThat(decision.confidence()).isEqualTo(0.74);
        assertThat(decision.reasons()).contains("LOGIN_FAILED_SIGNAL");
    }

    @Test
    void failsWithExplicitStatusWhenGatewayIsMissing() {
        DeepSeekLlmAgentDecisionService service = new DeepSeekLlmAgentDecisionService(
                fixedProvider(null),
                new LlmAgentDecisionParser(new ObjectMapper())
        );

        assertThatThrownBy(() -> service.decide(new AgentContext("u1", "title", "description")))
                .isInstanceOf(LlmDecisionException.class)
                .hasMessageContaining("NO_LLM_GATEWAY");
    }

    private static ObjectProvider<DeepSeekChatGateway> fixedProvider(DeepSeekChatGateway gateway) {
        return new ObjectProvider<>() {
            @Override
            public DeepSeekChatGateway getObject(Object... args) {
                return gateway;
            }

            @Override
            public DeepSeekChatGateway getIfAvailable() {
                return gateway;
            }

            @Override
            public DeepSeekChatGateway getIfUnique() {
                return gateway;
            }

            @Override
            public DeepSeekChatGateway getObject() {
                return gateway;
            }
        };
    }
}
