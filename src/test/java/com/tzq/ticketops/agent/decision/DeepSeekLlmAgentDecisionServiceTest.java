package com.tzq.ticketops.agent.decision;

import tools.jackson.databind.ObjectMapper;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void promptContainsStrictSchemaAndGoldenExamplesForPendingActions() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        DeepSeekLlmAgentDecisionService service = new DeepSeekLlmAgentDecisionService(
                fixedProvider(prompt -> {
                    capturedPrompt.set(prompt);
                    return """
                            {
                              "category": "ACCOUNT_LOCKED",
                              "priority": "P2",
                              "riskLevel": "NEEDS_APPROVAL",
                              "confidence": 0.91,
                              "sopQuery": "account locked SOP",
                              "toolIntents": [],
                              "pendingActions": [
                                {
                                  "actionType": "UNLOCK_ACCOUNT",
                                  "requiresApproval": true
                                }
                              ],
                              "internalSuggestion": "Submit unlock request.",
                              "userReplyDraft": "Your account appears locked.",
                              "reasonCode": "ACCOUNT_LOCKED_SIGNAL"
                            }
                            """;
                }),
                new LlmAgentDecisionParser(new ObjectMapper())
        );

        service.decide(new AgentContext(
                "mock-user-001",
                "OA login failed",
                "I cannot log in to OA. It says my account is locked."
        ));

        assertThat(capturedPrompt.get())
                .contains("\"pendingActions\": [")
                .contains("\"actionType\": \"UNLOCK_ACCOUNT\"")
                .contains("\"actionType\": \"GRANT_PERMISSION\"")
                .contains("\"requiresApproval\": true")
                .contains("For READ_ONLY or REJECT riskLevel, pendingActions must be []")
                .contains("Golden example: account locked")
                .contains("Golden example: permission request")
                .contains("Do not use actionType values such as unlock, resetPassword, closeTicket, or grantPermissionNow.");
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
