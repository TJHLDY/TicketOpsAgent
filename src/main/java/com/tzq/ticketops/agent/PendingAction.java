package com.tzq.ticketops.agent;

public record PendingAction(
        PendingActionType type,
        String summary
) {
}
