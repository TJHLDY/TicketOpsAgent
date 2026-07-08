package com.tzq.ticketops.agent.decision;

import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmAgentDecisionParserTest {

    private final LlmAgentDecisionParser parser = new LlmAgentDecisionParser(new ObjectMapper());

    @Test
    void parsesValidLlmJsonIntoAgentDecision() {
        String json = """
                {
                  "category": "ACCOUNT_LOCKED",
                  "priority": "P2",
                  "riskLevel": "NEEDS_APPROVAL",
                  "confidence": 0.92,
                  "sopQuery": "account locked SOP",
                  "toolIntents": [
                    {
                      "toolName": "getAccountStatus",
                      "arguments": {
                        "userId": "mock-user-001"
                      }
                    }
                  ],
                  "pendingActions": [
                    {
                      "actionType": "UNLOCK_ACCOUNT",
                      "requiresApproval": true
                    }
                  ],
                  "internalSuggestion": "Submit account unlock request.",
                  "userReplyDraft": "Your account appears locked.",
                  "reasonCode": "ACCOUNT_LOCKED_SIGNAL"
                }
                """;

        AgentDecision decision = parser.parse(json);

        assertThat(decision.category()).isEqualTo(TicketCategory.ACCOUNT_LOCKED);
        assertThat(decision.priority()).isEqualTo(TicketPriority.P2);
        assertThat(decision.riskLevel()).isEqualTo(RiskLevel.NEEDS_APPROVAL);
        assertThat(decision.confidence()).isEqualTo(0.92);
        assertThat(decision.toolIntents()).singleElement().satisfies(tool -> {
            assertThat(tool.toolName()).isEqualTo("getAccountStatus");
            assertThat(tool.args()).containsEntry("userId", "mock-user-001");
        });
        assertThat(decision.pendingActionProposal()).satisfies(action -> {
            assertThat(action.actionType()).isEqualTo(PendingActionType.UNLOCK_ACCOUNT);
            assertThat(action.requiresApproval()).isTrue();
        });
        assertThat(decision.reasons()).contains("ACCOUNT_LOCKED_SIGNAL");
    }

    @Test
    void rejectsUnknownToolName() {
        String json = """
                {
                  "category": "ACCOUNT_LOCKED",
                  "priority": "P2",
                  "riskLevel": "NEEDS_APPROVAL",
                  "confidence": 0.9,
                  "toolIntents": [
                    {
                      "toolName": "unlockAccountNow",
                      "arguments": {
                        "userId": "mock-user-001"
                      }
                    }
                  ],
                  "pendingActions": [],
                  "reasonCode": "BAD_TOOL"
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(LlmDecisionException.class)
                .hasMessageContaining("UNAUTHORIZED_TOOL");
    }

    @Test
    void rejectsWriteActionWithoutApproval() {
        String json = """
                {
                  "category": "PERMISSION_REQUEST",
                  "priority": "P3",
                  "riskLevel": "READ_ONLY",
                  "confidence": 0.8,
                  "toolIntents": [],
                  "pendingActions": [
                    {
                      "actionType": "GRANT_PERMISSION",
                      "requiresApproval": false
                    }
                  ],
                  "reasonCode": "UNSAFE_WRITE"
                }
                """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(LlmDecisionException.class)
                .hasMessageContaining("WRITE_ACTION_REQUIRES_APPROVAL");
    }

    @Test
    void wrapsMalformedJsonAsParseError() {
        assertThatThrownBy(() -> parser.parse("{not-json"))
                .isInstanceOf(LlmDecisionException.class)
                .hasMessageContaining("PARSE_ERROR");
    }
}
