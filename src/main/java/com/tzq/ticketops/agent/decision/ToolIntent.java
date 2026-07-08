package com.tzq.ticketops.agent.decision;

import java.util.Map;

public record ToolIntent(
        String toolName,
        Map<String, String> args
) {
}
