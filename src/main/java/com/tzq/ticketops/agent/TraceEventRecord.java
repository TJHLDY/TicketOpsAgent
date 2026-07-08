package com.tzq.ticketops.agent;

import java.time.Instant;

public record TraceEventRecord(
        long id,
        String ticketId,
        int stepOrder,
        String step,
        String detail,
        Instant createdAt
) {
}
