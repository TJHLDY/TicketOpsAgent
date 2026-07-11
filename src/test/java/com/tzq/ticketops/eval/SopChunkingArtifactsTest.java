package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SopChunkingArtifactsTest {

    @Test
    void vectorRagGuideDocumentsChunkingCitationAndBoundary() throws Exception {
        String guide = Files.readString(Path.of("docs", "rag", "vector-rag.md"));

        assertThat(guide)
                .contains("TokenTextSplitter")
                .contains("256-token")
                .contains("{sopId}#chunk-{index}")
                .contains("chunkId")
                .contains("chunkIndex")
                .contains("totalChunks")
                .contains("derived index artifacts")
                .contains("not a production indexing pipeline");
    }

    @Test
    void readmeExposesSopDocumentChunking() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## SOP Document Chunking")
                .contains("TokenTextSplitter")
                .contains("SOP-ACCOUNT-LOCKED#chunk-0")
                .contains("chunkId")
                .contains("chunkIndex")
                .contains("totalChunks");
    }
}
