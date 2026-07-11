package com.tzq.ticketops.agent;

import java.util.List;
import java.util.Objects;

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
    public static final int MAX_DRAFT_LENGTH = 4000;

    public AgentResponse {
        suggestion = boundDraft(suggestion);
        replyDraft = boundDraft(replyDraft);
    }

    private static String boundDraft(String value) {
        String draft = Objects.requireNonNull(value, "draft");
        if (draft.length() <= MAX_DRAFT_LENGTH) {
            return draft;
        }
        int endIndex = MAX_DRAFT_LENGTH;
        if (Character.isHighSurrogate(draft.charAt(endIndex - 1))
                && Character.isLowSurrogate(draft.charAt(endIndex))) {
            endIndex--;
        }
        return draft.substring(0, endIndex);
    }
}
