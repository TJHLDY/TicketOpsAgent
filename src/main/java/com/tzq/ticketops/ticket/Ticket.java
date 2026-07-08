package com.tzq.ticketops.ticket;

import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;

import java.time.Instant;

public record Ticket(
        String id,
        String requesterId,
        String title,
        String description,
        TicketStatus status,
        TicketCategory category,
        TicketPriority priority,
        RiskLevel riskLevel,
        Instant updatedAt,
        Instant createdAt
) {
}
