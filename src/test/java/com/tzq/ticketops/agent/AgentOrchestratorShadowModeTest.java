package com.tzq.ticketops.agent;

import com.tzq.ticketops.agent.decision.AgentContext;
import com.tzq.ticketops.agent.decision.AgentDecision;
import com.tzq.ticketops.agent.decision.AgentDecisionPort;
import com.tzq.ticketops.agent.decision.AgentMode;
import com.tzq.ticketops.agent.decision.PendingActionProposal;
import com.tzq.ticketops.agent.decision.ToolIntent;
import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorShadowModeTest {

    @Test
    void shadowModeReturnsDeterministicResultAndRecordsLlmDecision() {
        AgentDecisionPort shadowDecisionPort = new AgentDecisionPort() {
            @Override
            public AgentDecision decide(AgentContext context) {
                return new AgentDecision(
                        TicketCategory.LOGIN_FAILED,
                        TicketPriority.P3,
                        RiskLevel.READ_ONLY,
                        "shadow login failure",
                        List.of(new ToolIntent("unexpectedTool", java.util.Map.of())),
                        new PendingActionProposal(PendingActionType.CLOSE_TICKET, false),
                        "shadow suggestion",
                        "shadow reply",
                        0.61,
                        List.of("fake shadow decision for comparison")
                );
            }
        };
        AgentOrchestrator orchestrator = AgentOrchestrator.createWithDecisionMode(
                AgentMode.SHADOW,
                shadowDecisionPort,
                new SopSearchService(),
                new MockAccountStatusTool(),
                new MockUserPermissionsTool()
        );

        AgentResponse response = orchestrator.handle(new AgentRequest(
                "mock-user-001",
                "OA 登录失败",
                "提示账号已锁定，帮我恢复一下。"
        ));

        assertThat(response.category()).isEqualTo(TicketCategory.ACCOUNT_LOCKED);
        assertThat(response.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.toolName()).isEqualTo("getAccountStatus"));
        assertThat(response.traceEvents()).extracting(TraceEvent::step)
                .contains("LLM_SHADOW");
        assertThat(response.traceEvents()).extracting(TraceEvent::detail)
                .anySatisfy(detail -> assertThat(detail)
                        .contains("category=LOGIN_FAILED")
                        .contains("confidence=0.61")
                        .contains("toolIntents=unexpectedTool"));
    }
}
