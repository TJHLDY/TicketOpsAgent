package com.tzq.ticketops.agent;

import java.util.List;

public record AgentExecutionLog(
        String ticketId,
        List<TraceEvent> traceEvents,
        List<ToolCallRecord> toolCalls,
        List<PendingAction> pendingActions
) {
}
