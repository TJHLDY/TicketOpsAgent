package com.tzq.ticketops.agent;

public record SopReference(
        String id,
        String title,
        String source,
        double similarity,
        String chunkId,
        int chunkIndex,
        int totalChunks
) {
}
