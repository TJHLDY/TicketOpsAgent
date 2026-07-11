package com.tzq.ticketops.rag;

import com.tzq.ticketops.agent.SopReference;

import java.util.Optional;

public record SopSearchResult(
        SopSearchStatus status,
        Optional<SopReference> reference,
        double bestScore,
        double threshold,
        String embeddingProvider
) {
}
