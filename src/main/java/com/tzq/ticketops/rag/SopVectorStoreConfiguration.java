package com.tzq.ticketops.rag;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SopVectorStoreConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "ticketops.rag",
            name = "embedding-provider",
            havingValue = "offline",
            matchIfMissing = true
    )
    EmbeddingModel offlineEmbeddingModel() {
        return new OfflineFeatureHashEmbeddingModel();
    }

    @Bean
    VectorStoreRetriever sopVectorStoreRetriever(
            EmbeddingModel embeddingModel,
            SopDocumentSource documentSource
    ) {
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        return new RefreshingSopVectorStoreRetriever(vectorStore, documentSource);
    }
}
