package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentResponse;
import com.tzq.ticketops.agent.PendingAction;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.SopReference;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import com.tzq.ticketops.agent.ToolCallRecord;
import com.tzq.ticketops.agent.TraceEvent;

import java.util.List;

public record ChatResponse(
        String ticketId,
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
    public static ChatResponse from(String ticketId, AgentResponse response) {
        return new ChatResponse(
                ticketId,
                response.category(),
                response.priority(),
                response.riskLevel(),
                response.retrievedDocuments(),
                response.toolCalls(),
                response.suggestion(),
                response.replyDraft(),
                response.pendingActions(),
                response.traceEvents()
        );
    }
}
