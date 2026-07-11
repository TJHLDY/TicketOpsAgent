package com.tzq.ticketops.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.VectorStoreRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class RefreshingSopVectorStoreRetriever implements VectorStoreRetriever {

    static final String TITLE_METADATA = "sop_title";
    static final String SOURCE_METADATA = "sop_source";

    private final VectorStore vectorStore;
    private final SopDocumentSource documentSource;
    private List<String> indexedIds = List.of();
    private List<SopDocument> indexedDocuments = List.of();

    RefreshingSopVectorStoreRetriever(VectorStore vectorStore, SopDocumentSource documentSource) {
        this.vectorStore = vectorStore;
        this.documentSource = documentSource;
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
            vectorDocuments.add(new Document(
                    document.id(),
                    document.title() + "\n" + document.content(),
                    Map.of(
                            TITLE_METADATA, document.title(),
                            SOURCE_METADATA, document.source()
                    )
            ));
        }
        if (!vectorDocuments.isEmpty()) {
            vectorStore.add(vectorDocuments);
        }

        indexedIds = documents.stream().map(SopDocument::id).toList();
        indexedDocuments = documents;
    }
}
