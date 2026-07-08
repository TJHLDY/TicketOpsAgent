package com.tzq.ticketops.agent;

import java.time.Instant;

public record PendingActionRecord(
        long id,
        String ticketId,
        int actionOrder,
        PendingActionType actionType,
        String summary,
        PendingActionStatus status,
        String reviewerId,
        String reviewComment,
        Instant reviewedAt,
        String executionStatus,
        Instant createdAt
) {
}
