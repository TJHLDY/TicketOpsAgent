package com.tzq.ticketops.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class SopDocumentChunker {

    static final String SOP_ID_METADATA = "sop_id";
    static final String TITLE_METADATA = "sop_title";
    static final String SOURCE_METADATA = "sop_source";
    static final String CHUNK_INDEX_METADATA = "chunk_index";
    static final String TOTAL_CHUNKS_METADATA = "total_chunks";

    private static final int CHUNK_SIZE_TOKENS = 256;

    private final TokenTextSplitter splitter;

    SopDocumentChunker() {
        this(TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE_TOKENS)
                .withMinChunkSizeChars(20)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(1000)
                .withKeepSeparator(true)
                .build());
    }

    SopDocumentChunker(TokenTextSplitter splitter) {
        this.splitter = splitter;
    }

    List<Document> chunk(SopDocument sop) {
        Document parent = new Document(
                sop.id(),
                sop.title() + "\n" + sop.content(),
                Map.of(
                        SOP_ID_METADATA, sop.id(),
                        TITLE_METADATA, sop.title(),
                        SOURCE_METADATA, sop.source()
                )
        );
        List<Document> splitDocuments = splitter.split(parent);
        List<Document> chunks = new ArrayList<>(splitDocuments.size());
        for (int index = 0; index < splitDocuments.size(); index++) {
            Document split = splitDocuments.get(index);
            Map<String, Object> metadata = new HashMap<>(split.getMetadata());
            metadata.put(SOP_ID_METADATA, sop.id());
            metadata.put(TITLE_METADATA, sop.title());
            metadata.put(SOURCE_METADATA, sop.source());
            metadata.put(CHUNK_INDEX_METADATA, index);
            metadata.put(TOTAL_CHUNKS_METADATA, splitDocuments.size());
            chunks.add(new Document(
                    sop.id() + "#chunk-" + index,
                    split.getText(),
                    metadata
            ));
        }
        return List.copyOf(chunks);
    }
}
