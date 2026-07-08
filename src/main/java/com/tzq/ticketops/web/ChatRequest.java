package com.tzq.ticketops.web;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank String requesterId,
        @NotBlank String title,
        @NotBlank String description
) {
}
