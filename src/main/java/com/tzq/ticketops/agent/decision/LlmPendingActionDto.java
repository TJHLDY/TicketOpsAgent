package com.tzq.ticketops.agent.decision;

public record LlmPendingActionDto(
        String actionType,
        boolean requiresApproval
) {
}
