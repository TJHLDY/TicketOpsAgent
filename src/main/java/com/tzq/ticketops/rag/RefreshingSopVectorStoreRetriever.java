package com.tzq.ticketops.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;

import java.util.ArrayList;
import java.util.List;

final class RefreshingSopVectorStoreRetriever implements VectorStoreRetriever {

    private final VectorStore vectorStore;
    private final SopDocumentSource documentSource;
    private final SopDocumentChunker chunker;
    private List<String> indexedIds = List.of();
    private List<SopDocument> indexedDocuments = List.of();

    RefreshingSopVectorStoreRetriever(VectorStore vectorStore, SopDocumentSource documentSource) {
        this(vectorStore, documentSource, new SopDocumentChunker());
    }

    RefreshingSopVectorStoreRetriever(
            VectorStore vectorStore,
            SopDocumentSource documentSource,
            SopDocumentChunker chunker
    ) {
        this.vectorStore = vectorStore;
        this.documentSource = documentSource;
        this.chunker = chunker;
    }

    @Override
    public synchronized List<Document> similaritySearch(SearchRequest request) {
        refreshIfChanged();
        return vectorStore.similaritySearch(request);
    }

    private void refreshIfChanged() {
        List<SopDocument> documents = List.copyOf(documentSource.load());
        if (documents.equals(indexedDocuments)) {
            return;
        }

        if (!indexedIds.isEmpty()) {
            vectorStore.delete(indexedIds);
        }

        List<Document> vectorDocuments = new ArrayList<>();
        for (SopDocument document : documents) {
            vectorDocuments.addAll(chunker.chunk(document));
        }
        if (!vectorDocuments.isEmpty()) {
            vectorStore.add(vectorDocuments);
        }

        indexedIds = vectorDocuments.stream().map(Document::getId).toList();
        indexedDocuments = documents;
    }
}
