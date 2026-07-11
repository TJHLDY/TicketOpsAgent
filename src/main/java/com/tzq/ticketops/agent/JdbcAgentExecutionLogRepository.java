package com.tzq.ticketops.agent;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class JdbcAgentExecutionLogRepository implements AgentExecutionLogRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentExecutionLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(AgentExecutionLog log) {
        deleteExisting(log.ticketId());
        saveTraceEvents(log.ticketId(), log.traceEvents());
        saveToolCalls(log.ticketId(), log.toolCalls());
        savePendingActions(log.ticketId(), log.pendingActions());
        saveMessages(log.ticketId(), log.suggestion(), log.replyDraft());
    }

    @Override
    public Optional<AgentExecutionLog> findByTicketId(String ticketId) {
        List<TraceEvent> traceEvents = jdbcTemplate.query(
                """
                        select step, detail
                        from agent_trace
                        where ticket_id = ?
                        order by step_order
                        """,
                (rs, rowNum) -> new TraceEvent(rs.getString("step"), rs.getString("detail")),
                ticketId
        );
        List<ToolCallRecord> toolCalls = jdbcTemplate.query(
                """
                        select tool_name, arguments_json, result_summary
                        from tool_call_log
                        where ticket_id = ?
                        order by tool_order
                        """,
                (rs, rowNum) -> new ToolCallRecord(
                        rs.getString("tool_name"),
                        readArguments(rs.getString("arguments_json")),
                        rs.getString("result_summary")
                ),
                ticketId
        );
        List<PendingAction> pendingActions = jdbcTemplate.query(
                """
                        select action_type, summary
                        from pending_action
                        where ticket_id = ?
                        order by action_order
                        """,
                (rs, rowNum) -> new PendingAction(
                        PendingActionType.valueOf(rs.getString("action_type")),
                        rs.getString("summary")
                ),
                ticketId
        );
        List<TicketMessageRecord> messages = findMessagesByTicketId(ticketId);

        if (traceEvents.isEmpty() && toolCalls.isEmpty() && pendingActions.isEmpty() && messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AgentExecutionLog(
                ticketId,
                traceEvents,
                toolCalls,
                pendingActions,
                messageContent(messages, TicketMessageSenderType.INTERNAL_SUGGESTION),
                messageContent(messages, TicketMessageSenderType.USER_REPLY_DRAFT)
        ));
    }

    @Override
    public List<TraceEventRecord> findTraceEventsByTicketId(String ticketId) {
        return jdbcTemplate.query(
                """
                        select id, ticket_id, step_order, step, detail, created_at
                        from agent_trace
                        where ticket_id = ?
                        order by step_order
                        """,
                (rs, rowNum) -> new TraceEventRecord(
                        rs.getLong("id"),
                        rs.getString("ticket_id"),
                        rs.getInt("step_order"),
                        rs.getString("step"),
                        rs.getString("detail"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                ticketId
        );
    }

    @Override
    public List<ToolCallLogRecord> findToolCallsByTicketId(String ticketId) {
        return jdbcTemplate.query(
                """
                        select id, ticket_id, tool_order, tool_name, arguments_json, result_summary, created_at
                        from tool_call_log
                        where ticket_id = ?
                        order by tool_order
                        """,
                (rs, rowNum) -> new ToolCallLogRecord(
                        rs.getLong("id"),
                        rs.getString("ticket_id"),
                        rs.getInt("tool_order"),
                        rs.getString("tool_name"),
                        readArguments(rs.getString("arguments_json")),
                        rs.getString("result_summary"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                ticketId
        );
    }

    @Override
    public List<PendingActionRecord> findPendingActionsByTicketId(String ticketId) {
        return jdbcTemplate.query(
                """
                        select id, ticket_id, action_order, action_type, summary, status,
                               reviewer_id, review_comment, reviewed_at, execution_status, created_at
                        from pending_action
                        where ticket_id = ?
                        order by action_order
                        """,
                (rs, rowNum) -> mapPendingAction(rs),
                ticketId
        );
    }

    @Override
    public List<TicketMessageRecord> findMessagesByTicketId(String ticketId) {
        return jdbcTemplate.query(
                """
                        select id, ticket_id, message_order, sender_type, content, created_at
                        from ticket_message
                        where ticket_id = ?
                          and sender_type in (?, ?)
                        order by message_order, id
                        """,
                (rs, rowNum) -> new TicketMessageRecord(
                        rs.getLong("id"),
                        rs.getString("ticket_id"),
                        rs.getInt("message_order"),
                        TicketMessageSenderType.valueOf(rs.getString("sender_type")),
                        rs.getString("content"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                ticketId,
                TicketMessageSenderType.INTERNAL_SUGGESTION.name(),
                TicketMessageSenderType.USER_REPLY_DRAFT.name()
        );
    }

    @Override
    public Optional<PendingActionRecord> findPendingActionById(long actionId) {
        return jdbcTemplate.query(
                """
                        select id, ticket_id, action_order, action_type, summary, status,
                               reviewer_id, review_comment, reviewed_at, execution_status, created_at
                        from pending_action
                        where id = ?
                        """,
                (rs, rowNum) -> mapPendingAction(rs),
                actionId
        ).stream().findFirst();
    }

    @Override
    public void updatePendingActionReview(
            long actionId,
            PendingActionStatus status,
            String reviewerId,
            String reviewComment
    ) {
        jdbcTemplate.update(
                """
                        update pending_action
                        set status = ?, reviewer_id = ?, review_comment = ?, reviewed_at = current_timestamp
                        where id = ?
                        """,
                status.name(),
                reviewerId,
                reviewComment,
                actionId
        );
    }

    private void deleteExisting(String ticketId) {
        jdbcTemplate.update(
                "delete from ticket_message where ticket_id = ? and sender_type in (?, ?)",
                ticketId,
                TicketMessageSenderType.INTERNAL_SUGGESTION.name(),
                TicketMessageSenderType.USER_REPLY_DRAFT.name()
        );
        jdbcTemplate.update("delete from agent_trace where ticket_id = ?", ticketId);
        jdbcTemplate.update("delete from tool_call_log where ticket_id = ?", ticketId);
        jdbcTemplate.update("delete from pending_action where ticket_id = ?", ticketId);
    }

    private void saveTraceEvents(String ticketId, List<TraceEvent> traceEvents) {
        for (int i = 0; i < traceEvents.size(); i++) {
            TraceEvent event = traceEvents.get(i);
            jdbcTemplate.update(
                    "insert into agent_trace(ticket_id, step_order, step, detail) values (?, ?, ?, ?)",
                    ticketId,
                    i,
                    event.step(),
                    event.detail()
            );
        }
    }

    private void saveToolCalls(String ticketId, List<ToolCallRecord> toolCalls) {
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCallRecord call = toolCalls.get(i);
            jdbcTemplate.update(
                    "insert into tool_call_log(ticket_id, tool_order, tool_name, arguments_json, result_summary) values (?, ?, ?, ?, ?)",
                    ticketId,
                    i,
                    call.toolName(),
                    writeArguments(call.arguments()),
                    call.resultSummary()
            );
        }
    }

    private void savePendingActions(String ticketId, List<PendingAction> pendingActions) {
        for (int i = 0; i < pendingActions.size(); i++) {
            PendingAction action = pendingActions.get(i);
            jdbcTemplate.update(
                    "insert into pending_action(ticket_id, action_order, action_type, summary) values (?, ?, ?, ?)",
                    ticketId,
                    i,
                    action.type().name(),
                    action.summary()
            );
        }
    }

    private void saveMessages(String ticketId, String suggestion, String replyDraft) {
        saveMessage(ticketId, 0, TicketMessageSenderType.INTERNAL_SUGGESTION, suggestion);
        saveMessage(ticketId, 1, TicketMessageSenderType.USER_REPLY_DRAFT, replyDraft);
    }

    private void saveMessage(
            String ticketId,
            int messageOrder,
            TicketMessageSenderType senderType,
            String content
    ) {
        jdbcTemplate.update(
                "insert into ticket_message(ticket_id, message_order, sender_type, content) values (?, ?, ?, ?)",
                ticketId,
                messageOrder,
                senderType.name(),
                content
        );
    }

    private String messageContent(
            List<TicketMessageRecord> messages,
            TicketMessageSenderType senderType
    ) {
        return messages.stream()
                .filter(message -> message.senderType() == senderType)
                .map(TicketMessageRecord::content)
                .findFirst()
                .orElse("");
    }

    private String writeArguments(Map<String, String> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tool arguments", e);
        }
    }

    private Map<String, String> readArguments(String json) {
        try {
            return objectMapper.readValue(json, STRING_MAP);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize tool arguments", e);
        }
    }

    private PendingActionRecord mapPendingAction(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new PendingActionRecord(
                rs.getLong("id"),
                rs.getString("ticket_id"),
                rs.getInt("action_order"),
                PendingActionType.valueOf(rs.getString("action_type")),
                rs.getString("summary"),
                PendingActionStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                reviewedAt == null ? null : reviewedAt.toInstant(),
                rs.getString("execution_status"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
