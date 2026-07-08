package com.tzq.ticketops.agent;

public record AgentRequest(
        String requesterId,
        String title,
        String description
) {
}
