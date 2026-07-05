package com.tzq.ticketops.agent.decision;

import com.tzq.ticketops.agent.PendingActionType;

public record PendingActionProposal(
        PendingActionType actionType,
        boolean requiresApproval
) {
}
