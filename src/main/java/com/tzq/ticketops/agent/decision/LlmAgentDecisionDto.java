package com.tzq.ticketops.agent.decision;

import java.util.List;

public record LlmAgentDecisionDto(
        String category,
        String priority,
        String riskLevel,
        Double confidence,
        String sopQuery,
        List<LlmToolIntentDto> toolIntents,
        List<LlmPendingActionDto> pendingActions,
        String internalSuggestion,
        String userReplyDraft,
        String reasonCode
) {
}
