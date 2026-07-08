package com.tzq.ticketops.agent.decision;

import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;

import java.util.List;

public record AgentDecision(
        TicketCategory category,
        TicketPriority priority,
        RiskLevel riskLevel,
        String sopQuery,
        List<ToolIntent> toolIntents,
        PendingActionProposal pendingActionProposal,
        String internalSuggestion,
        String userReplyDraft,
        double confidence,
        List<String> reasons
) {
}
