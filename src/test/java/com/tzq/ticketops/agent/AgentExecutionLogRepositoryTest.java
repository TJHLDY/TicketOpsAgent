package com.tzq.ticketops.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
class AgentExecutionLogRepositoryTest {

    @Autowired
    AgentExecutionLogRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void savesTraceToolCallsAndPendingActionsForTicket() {
        AgentExecutionLog log = new AgentExecutionLog(
                "TICKET-100",
                List.of(
                        new TraceEvent("CLASSIFY", "category=ACCOUNT_LOCKED"),
                        new TraceEvent("TOOL_CALL", "tool=getAccountStatus, result=LOCKED")
                ),
                List.of(new ToolCallRecord("getAccountStatus", Map.of("userId", "mock-user-001"), "LOCKED")),
                List.of(new PendingAction(PendingActionType.UNLOCK_ACCOUNT, "等待人工确认后解锁账号 mock-user-001")),
                "Verify identity before approving an account unlock.",
                "Your account is locked. IT support will review the unlock request."
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
        assertThat(saved.suggestion())
                .isEqualTo("Verify identity before approving an account unlock.");
        assertThat(saved.replyDraft())
                .isEqualTo("Your account is locked. IT support will review the unlock request.");
        assertThat(repository.findMessagesByTicketId("TICKET-100"))
                .satisfiesExactly(
                        message -> {
                            assertThat(message.messageOrder()).isZero();
                            assertThat(message.senderType()).isEqualTo(TicketMessageSenderType.INTERNAL_SUGGESTION);
                            assertThat(message.content()).isEqualTo(saved.suggestion());
                        },
                        message -> {
                            assertThat(message.messageOrder()).isEqualTo(1);
                            assertThat(message.senderType()).isEqualTo(TicketMessageSenderType.USER_REPLY_DRAFT);
                            assertThat(message.content()).isEqualTo(saved.replyDraft());
                        }
                );
    }

    @Test
    void rollsBackAllEvidenceWhenDraftPersistenceFails() {
        String ticketId = "TICKET-ROLLBACK";
        AgentExecutionLog log = new AgentExecutionLog(
                ticketId,
                List.of(new TraceEvent("CLASSIFY", "category=ACCOUNT_LOCKED")),
                List.of(new ToolCallRecord("getAccountStatus", Map.of("userId", "mock-user-001"), "LOCKED")),
                List.of(new PendingAction(PendingActionType.UNLOCK_ACCOUNT, "Review account unlock.")),
                "x".repeat(4001),
                "Reply draft"
        );

        Throwable failure = catchThrowable(() -> repository.save(log));

        assertThat(failure).isNotNull();
        assertThat(rowCount("agent_trace", ticketId)).isZero();
        assertThat(rowCount("tool_call_log", ticketId)).isZero();
        assertThat(rowCount("pending_action", ticketId)).isZero();
        assertThat(rowCount("ticket_message", ticketId)).isZero();
    }

    @Test
    void replacingAgentEvidencePreservesUnrelatedTicketMessages() {
        String ticketId = "TICKET-PRESERVE-MESSAGES";
        jdbcTemplate.update(
                "insert into ticket_message(ticket_id, message_order, sender_type, content) values (?, ?, ?, ?)",
                ticketId,
                7,
                "REQUESTER_MESSAGE",
                "Original user-authored ticket message"
        );

        repository.save(new AgentExecutionLog(
                ticketId,
                List.of(new TraceEvent("CLASSIFY", "category=LOGIN_FAILED")),
                List.of(),
                List.of(),
                "Check the login failure SOP.",
                "IT support is reviewing your login issue."
        ));

        assertThat(jdbcTemplate.queryForObject(
                "select content from ticket_message where ticket_id = ? and sender_type = ?",
                String.class,
                ticketId,
                "REQUESTER_MESSAGE"
        )).isEqualTo("Original user-authored ticket message");
        assertThat(repository.findMessagesByTicketId(ticketId))
                .extracting(TicketMessageRecord::senderType)
                .containsExactly(
                        TicketMessageSenderType.INTERNAL_SUGGESTION,
                        TicketMessageSenderType.USER_REPLY_DRAFT
                );
    }

    private int rowCount(String table, String ticketId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where ticket_id = ?",
                Integer.class,
                ticketId
        );
    }
}
