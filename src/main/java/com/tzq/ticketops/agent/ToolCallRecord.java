package com.tzq.ticketops.agent;

import java.util.Map;

public record ToolCallRecord(
        String toolName,
        Map<String, String> arguments,
        String resultSummary
) {
}
