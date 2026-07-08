package com.tzq.ticketops.agent.decision;

public record AgentContext(
        String requesterId,
        String title,
        String description
) {
    public String text() {
        return title + "\n" + description;
    }
}
