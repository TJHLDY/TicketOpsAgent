package com.tzq.ticketops.agent;

import java.time.Instant;
import java.util.Map;

public record ToolCallLogRecord(
        long id,
        String ticketId,
        int toolOrder,
        String toolName,
        Map<String, String> arguments,
        String resultSummary,
        Instant createdAt
) {
}
