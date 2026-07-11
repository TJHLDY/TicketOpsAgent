package com.tzq.ticketops.observability;

import com.tzq.ticketops.agent.AgentResponse;
import com.tzq.ticketops.agent.RiskLevel;

public enum AgentRequestOutcome {
    COMPLETED,
    POLICY_REJECTED,
    RAG_REJECTED,
    TOOL_REJECTED,
    UNSUPPORTED,
    ERROR;

    public static AgentRequestOutcome from(AgentResponse response) {
        if (response.riskLevel() == RiskLevel.REJECT) {
            return POLICY_REJECTED;
        }
        if (hasTraceStep(response, "RAG_REJECT")) {
            return RAG_REJECTED;
        }
        if (hasTraceStep(response, "TOOL_REJECT")) {
            return TOOL_REJECTED;
        }
        if (response.retrievedDocuments().isEmpty() && response.toolCalls().isEmpty()) {
            return UNSUPPORTED;
        }
        return COMPLETED;
    }

    private static boolean hasTraceStep(AgentResponse response, String step) {
        return response.traceEvents().stream().anyMatch(event -> step.equals(event.step()));
    }
}
