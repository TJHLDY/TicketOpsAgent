package com.tzq.ticketops.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SopDocumentChunkerTest {

    @Test
    void createsDeterministicMetadataRichChunksWithSpringAiSplitter() {
        SopDocument sop = new SopDocument(
                "SOP-LONG",
                "Long account recovery SOP",
                "mock-sop/long-account-recovery.md",
                ("general policy context and operator checklist. ").repeat(900)
        );

        List<Document> chunks = new SopDocumentChunker().chunk(sop);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int index = 0; index < chunks.size(); index++) {
            Document chunk = chunks.get(index);
            assertThat(chunk.getId()).isEqualTo("SOP-LONG#chunk-" + index);
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getMetadata())
                    .containsEntry(SopDocumentChunker.SOP_ID_METADATA, "SOP-LONG")
                    .containsEntry(SopDocumentChunker.TITLE_METADATA, "Long account recovery SOP")
                    .containsEntry(SopDocumentChunker.SOURCE_METADATA, "mock-sop/long-account-recovery.md")
                    .containsEntry(SopDocumentChunker.CHUNK_INDEX_METADATA, index)
                    .containsEntry(SopDocumentChunker.TOTAL_CHUNKS_METADATA, chunks.size());
        }
    }

    @Test
    void keepsShortSopAsOneChunk() {
        SopDocument sop = new SopDocument(
                "SOP-SHORT",
                "Short SOP",
                "mock-sop/short.md",
                "Check account status before proposing an action."
        );

        List<Document> chunks = new SopDocumentChunker().chunk(sop);

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.getId()).isEqualTo("SOP-SHORT#chunk-0");
            assertThat(chunk.getMetadata())
                    .containsEntry(SopDocumentChunker.CHUNK_INDEX_METADATA, 0)
                    .containsEntry(SopDocumentChunker.TOTAL_CHUNKS_METADATA, 1);
        });
    }
}
