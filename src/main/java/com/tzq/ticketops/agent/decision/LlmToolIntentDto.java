package com.tzq.ticketops.agent.decision;

import java.util.Map;

public record LlmToolIntentDto(
        String toolName,
        Map<String, String> arguments
) {
}
