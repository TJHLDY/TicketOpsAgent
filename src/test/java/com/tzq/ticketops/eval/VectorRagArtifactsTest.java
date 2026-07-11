package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VectorRagArtifactsTest {

    @Test
    void vectorRagGuideDocumentsProvidersEvidenceAndProductionBoundary() throws Exception {
        Path guidePath = Path.of("docs", "rag", "vector-rag.md");

        assertThat(guidePath).exists();
        assertThat(Files.readString(guidePath))
                .contains("# Vector RAG")
                .contains("VectorStoreRetriever")
                .contains("offline")
                .contains("onnx")
                .contains("source citation")
                .contains("LOW_SIMILARITY")
                .contains("RAG_REJECT")
                .contains("SimpleVectorStore")
                .contains("not production")
                .contains("mvn -Ponnx");
    }

    @Test
    void onnxProfileIsExplicitAndOptional() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String onnxConfig = Files.readString(Path.of("src", "main", "resources", "application-onnx.yml"));

        assertThat(pom)
                .contains("<artifactId>spring-ai-vector-store</artifactId>")
                .contains("<id>onnx</id>")
                .contains("<artifactId>spring-ai-starter-model-transformers</artifactId>");
        assertThat(onnxConfig)
                .contains("embedding: transformers")
                .contains("embedding-provider: onnx")
                .contains("spring-ai/v2.0.0")
                .contains("TICKETOPS_ONNX_CACHE");
    }
}
