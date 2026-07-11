package com.tzq.ticketops.tools;

import com.tzq.ticketops.agent.ToolCallRecord;

public record ToolExecutionResult(
        ToolCallRecord toolCall,
        int budgetUsed,
        int budgetLimit
) {
}

