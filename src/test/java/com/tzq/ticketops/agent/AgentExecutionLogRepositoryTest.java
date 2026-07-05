package com.tzq.ticketops.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentExecutionLogRepositoryTest {

    @Autowired
    AgentExecutionLogRepository repository;

    @Test
    void savesTraceToolCallsAndPendingActionsForTicket() {
        AgentExecutionLog log = new AgentExecutionLog(
                "TICKET-100",
                List.of(
                        new TraceEvent("CLASSIFY", "category=ACCOUNT_LOCKED"),
                        new TraceEvent("TOOL_CALL", "tool=getAccountStatus, result=LOCKED")
                ),
                List.of(new ToolCallRecord("getAccountStatus", Map.of("userId", "mock-user-001"), "LOCKED")),
                List.of(new PendingAction(PendingActionType.UNLOCK_ACCOUNT, "等待人工确认后解锁账号 mock-user-001"))
        );

        repository.save(log);

        AgentExecutionLog saved = repository.findByTicketId("TICKET-100").orElseThrow();
        assertThat(saved.traceEvents()).extracting(TraceEvent::step)
                .containsExactly("CLASSIFY", "TOOL_CALL");
        assertThat(saved.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.toolName()).isEqualTo("getAccountStatus");
            assertThat(call.arguments()).containsEntry("userId", "mock-user-001");
            assertThat(call.resultSummary()).isEqualTo("LOCKED");
        });
        assertThat(saved.pendingActions()).singleElement().satisfies(action -> {
            assertThat(action.type()).isEqualTo(PendingActionType.UNLOCK_ACCOUNT);
            assertThat(action.summary()).contains("mock-user-001");
        });
    }
}
