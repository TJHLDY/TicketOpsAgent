package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentOrchestrator;
import com.tzq.ticketops.agent.AgentExecutionLog;
import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.observability.AgentTelemetry;
import com.tzq.ticketops.ticket.TicketService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControllerTest {

    @Test
    void chatCreatesOpenTicketBeforeRunningAgent() {
        TicketService ticketService = new TicketService();
        AgentController controller = new AgentController(
                AgentOrchestrator.createDefault(),
                ticketService,
                new NoopLogRepository(),
                AgentTelemetry.noop()
        );

        ChatResponse response = controller.chat(new ChatRequest(
                "mock-user-001",
                "OA 登录失败",
                "我登录 OA 系统失败，提示账号已锁定，帮我恢复一下。"
        ));

        assertThat(response.category()).isEqualTo(TicketCategory.ACCOUNT_LOCKED);
        assertThat(ticketService.findAll()).singleElement().satisfies(ticket -> {
            assertThat(response.ticketId()).isEqualTo(ticket.id());
            assertThat(ticket.requesterId()).isEqualTo("mock-user-001");
            assertThat(ticket.title()).isEqualTo("OA 登录失败");
        });
    }

    private static class NoopLogRepository implements AgentExecutionLogRepository {

        @Override
        public void save(AgentExecutionLog log) {
        }

        @Override
        public Optional<AgentExecutionLog> findByTicketId(String ticketId) {
            return Optional.empty();
        }
    }
}
