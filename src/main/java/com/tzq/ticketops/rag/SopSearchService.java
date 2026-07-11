package com.tzq.ticketops.rag;

import com.tzq.ticketops.agent.SopReference;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class SopSearchService {

    private final VectorStoreRetriever retriever;
    private final double similarityThreshold;
    private final int topK;
    private final String embeddingProvider;

    public SopSearchService() {
        this(offlineRetriever(), 0.30, 1, "offline");
    }

    @Autowired
    public SopSearchService(
            VectorStoreRetriever retriever,
            @Value("${ticketops.rag.similarity-threshold:0.30}") double similarityThreshold,
            @Value("${ticketops.rag.top-k:1}") int topK,
            @Value("${ticketops.rag.embedding-provider:offline}") String embeddingProvider
    ) {
        if (similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalArgumentException("similarityThreshold must be between 0 and 1");
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }
        this.retriever = retriever;
        this.similarityThreshold = similarityThreshold;
        this.topK = topK;
        this.embeddingProvider = embeddingProvider;
    }

    public static SopSearchService createOffline(double similarityThreshold) {
        return new SopSearchService(offlineRetriever(), similarityThreshold, 1, "offline");
    }

    public SopSearchResult search(String text) {
        if (text == null || text.isBlank()) {
            return new SopSearchResult(
                    SopSearchStatus.LOW_SIMILARITY,
                    Optional.empty(),
                    0,
                    similarityThreshold,
                    embeddingProvider
            );
        }
        SearchRequest request = SearchRequest.builder()
                .query(text)
                .topK(topK)
                .similarityThresholdAll()
                .build();
        List<Document> documents = retriever.similaritySearch(request);
        if (documents.isEmpty()) {
            return new SopSearchResult(
                    SopSearchStatus.NO_DOCUMENTS,
                    Optional.empty(),
                    0,
                    similarityThreshold,
                    embeddingProvider
            );
        }

        Document best = documents.get(0);
        double score = best.getScore() == null ? 0 : best.getScore();
        if (score < similarityThreshold) {
            return new SopSearchResult(
                    SopSearchStatus.LOW_SIMILARITY,
                    Optional.empty(),
                    score,
                    similarityThreshold,
                    embeddingProvider
            );
        }

        SopReference reference = new SopReference(
                metadata(best, SopDocumentChunker.SOP_ID_METADATA),
                metadata(best, SopDocumentChunker.TITLE_METADATA),
                metadata(best, SopDocumentChunker.SOURCE_METADATA),
                score,
                best.getId(),
                integerMetadata(best, SopDocumentChunker.CHUNK_INDEX_METADATA),
                integerMetadata(best, SopDocumentChunker.TOTAL_CHUNKS_METADATA)
        );
        return new SopSearchResult(
                SopSearchStatus.ACCEPTED,
                Optional.of(reference),
                score,
                similarityThreshold,
                embeddingProvider
        );
    }

    private String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value == null) {
            throw new IllegalStateException("Retrieved SOP document is missing metadata: " + key);
        }
        return value.toString();
    }

    private int integerMetadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            throw new IllegalStateException("Retrieved SOP document is missing metadata: " + key);
        }
        return Integer.parseInt(value.toString());
    }

    private static VectorStoreRetriever offlineRetriever() {
        OfflineFeatureHashEmbeddingModel embeddingModel = new OfflineFeatureHashEmbeddingModel();
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        return new RefreshingSopVectorStoreRetriever(vectorStore, DefaultSopDocuments::all);
    }
}
