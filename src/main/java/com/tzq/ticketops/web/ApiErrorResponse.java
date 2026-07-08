package com.tzq.ticketops.web;

import java.time.Instant;

public record ApiErrorResponse(
        String errorCode,
        String message,
        String path,
        Instant timestamp
) {
}
