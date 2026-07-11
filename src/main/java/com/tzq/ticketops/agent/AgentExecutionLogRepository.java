package com.tzq.ticketops.agent;

import java.util.Optional;
import java.util.List;

public interface AgentExecutionLogRepository {

    void save(AgentExecutionLog log);

    Optional<AgentExecutionLog> findByTicketId(String ticketId);

    default List<TraceEventRecord> findTraceEventsByTicketId(String ticketId) {
        return List.of();
    }

    default List<ToolCallLogRecord> findToolCallsByTicketId(String ticketId) {
        return List.of();
    }

    default List<PendingActionRecord> findPendingActionsByTicketId(String ticketId) {
        return List.of();
    }

    default List<TicketMessageRecord> findMessagesByTicketId(String ticketId) {
        return List.of();
    }

    default Optional<PendingActionRecord> findPendingActionById(long actionId) {
        return Optional.empty();
    }

    default void updatePendingActionReview(
            long actionId,
            PendingActionStatus status,
            String reviewerId,
            String reviewComment
    ) {
    }
}
