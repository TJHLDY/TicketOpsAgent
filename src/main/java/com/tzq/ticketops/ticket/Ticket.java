package com.tzq.ticketops.ticket;

import java.time.Instant;

public record Ticket(
        String id,
        String requesterId,
        String title,
        String description,
        TicketStatus status,
        Instant createdAt
) {
}
