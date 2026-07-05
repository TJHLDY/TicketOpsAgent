package com.tzq.ticketops.agent;

import java.util.List;

public record AgentResponse(
        TicketCategory category,
        TicketPriority priority,
        RiskLevel riskLevel,
        List<SopReference> retrievedDocuments,
        List<ToolCallRecord> toolCalls,
        String suggestion,
        String replyDraft,
        List<PendingAction> pendingActions,
        List<TraceEvent> traceEvents
) {
}
