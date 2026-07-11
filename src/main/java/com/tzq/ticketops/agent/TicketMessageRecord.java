package com.tzq.ticketops.agent;

import java.time.Instant;

public record TicketMessageRecord(
        long id,
        String ticketId,
        int messageOrder,
        TicketMessageSenderType senderType,
        String content,
        Instant createdAt
) {
}
