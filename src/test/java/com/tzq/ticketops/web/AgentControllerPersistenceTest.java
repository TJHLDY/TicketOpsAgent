package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.TraceEvent;
import com.tzq.ticketops.ticket.Ticket;
import com.tzq.ticketops.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AgentControllerPersistenceTest {

    @Autowired
    AgentController controller;

    @Autowired
    TicketService ticketService;

    @Autowired
    AgentExecutionLogRepository logRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTickets() {
        ticketService.clear();
        jdbcTemplate.update("delete from mock_user_account");
        jdbcTemplate.update(
                "insert into mock_user_account(user_id, account_status, display_name) values (?, ?, ?)",
                "mock-user-001",
                "LOCKED",
                "张三"
        );
    }

    @Test
    void chatPersistsTicketTraceToolCallAndPendingAction() {
        controller.chat(new ChatRequest(
                "mock-user-001",
                "OA 登录失败",
                "我登录 OA 系统失败，提示账号已锁定，帮我恢复一下。"
        ));

        Ticket ticket = ticketService.findAll().get(0);
        var log = logRepository.findByTicketId(ticket.id()).orElseThrow();

        assertThat(log.traceEvents()).extracting(TraceEvent::step)
                .containsExactly("CLASSIFY", "RAG_RETRIEVE", "TOOL_CALL", "DRAFT_GENERATE", "PENDING_ACTION");
        assertThat(log.toolCalls()).singleElement()
                .satisfies(call -> assertThat(call.resultSummary()).isEqualTo("LOCKED"));
        assertThat(log.pendingActions()).singleElement()
                .satisfies(action -> assertThat(action.type()).isEqualTo(PendingActionType.UNLOCK_ACCOUNT));
    }
}
