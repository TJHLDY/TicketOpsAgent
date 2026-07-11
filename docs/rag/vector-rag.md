# Vector RAG

TicketOpsAgent retrieves mock IT support SOP documents through Spring AI `VectorStoreRetriever`. Retrieval is embedding-based, returns a source citation, and stops the workflow when the best score is below the configured threshold.

## Runtime flow

1. `JdbcSopDocumentSource` reads the current `sop_document` rows.
2. `SopDocumentChunker` applies Spring AI `TokenTextSplitter` with a 256-token target size.
3. Splitter output is rebuilt with deterministic `{sopId}#chunk-{index}` IDs and parent citation metadata.
4. `RefreshingSopVectorStoreRetriever` compares the current rows with the indexed snapshot and refreshes the prototype chunk index only when they change.
5. The selected `EmbeddingModel` converts SOP chunks and the rewritten query into numeric vectors.
6. Spring AI `SimpleVectorStore` performs cosine-similarity search.
7. `SopSearchService` returns the best chunk only when its score reaches the threshold.
8. Accepted retrieval produces `RAG_RETRIEVE`; rejected retrieval produces `RAG_REJECT` before any tool or pending action.

`SopSearchService` depends on the read-only `VectorStoreRetriever` interface. Mutable indexing is encapsulated inside the refreshing adapter.

The JDBC source fails closed: an empty `sop_document` table yields `NO_DOCUMENTS`; it never silently substitutes built-in documents. Built-in documents are used only by the explicit no-Spring offline factory for unit/demo construction.

## Document chunking

`SopDocumentChunker` uses Spring AI `TokenTextSplitter` with a 256-token target, a small minimum embeddable chunk, separators retained, and a bounded maximum chunk count. The splitter preserves source metadata; TicketOpsAgent then assigns deterministic chunk IDs so index refresh can delete exactly the old derived chunks.

Chunk metadata includes:

- parent SOP ID;
- SOP title and source;
- `chunkIndex`;
- `totalChunks`.

A short SOP produces one chunk. A longer SOP can produce multiple chunks, and a query may cite a later section while `SopReference.id` continues to identify the parent SOP. Chunk rows are derived index artifacts and are not stored as authoritative SOP records.

## Providers

### offline

Default provider: deterministic 256-dimensional word/CJK n-gram feature hashing with bilingual normalization.

- no network;
- no API key;
- repeatable tests and demos;
- not a trained semantic model.

```powershell
mvn test
mvn spring-boot:run
```

### onnx

Optional provider: Spring AI local ONNX Transformers embeddings.

The large ONNX runtime is isolated behind Maven profile `onnx`, so it does not inflate the default executable artifact or acceptance path.

```powershell
mvn -Ponnx spring-boot:run "-Dspring-boot.run.profiles=onnx"
```

The first call can download and cache model resources under `TICKETOPS_ONNX_CACHE` or the configured temporary directory. Model and tokenizer URLs are pinned to the Spring AI `v2.0.0` release tag instead of a mutable branch. No cloud embedding key is required.

## Retrieval evidence

An accepted `RAG_RETRIEVE` trace records:

- `status=ACCEPTED`;
- document ID and title;
- `source` citation;
- `chunkId`, `chunkIndex`, and `totalChunks`;
- actual similarity score;
- configured threshold;
- embedding provider.

The user-facing `retrievedDocuments` field returns the same parent ID/title/source/score citation plus the matched chunk identity.

## Low-similarity refusal

If the best score is lower than `ticketops.rag.similarity-threshold`, the result status is `LOW_SIMILARITY` and the Agent:

- writes `RAG_REJECT` with best score, threshold, and provider;
- returns a human-transfer suggestion and reply draft;
- does not call `getAccountStatus` or `getUserPermissions`;
- does not create an unlock or permission pending action.

Retrieved document content never decides category, risk, tool name, tool arguments, or write-like pending actions.

## Validation

```powershell
mvn test "-Dtest=OfflineFeatureHashEmbeddingModelTest,SopVectorRetrievalTest,SopSearchServicePersistenceTest,AgentOrchestratorTest"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

The vector tests cover bilingual account-lock retrieval, permission and MFA retrieval, unrelated-query refusal, database refresh, stale-chunk deletion, deterministic chunk metadata, later-section retrieval, source citation, and the no-tool/no-pending-action refusal boundary.

Latest local ONNX smoke evidence:

- Chinese account-lock query -> `SOP-ACCOUNT-LOCKED`, source `mock-sop/account-locked.md`, provider `onnx`, score `0.568`.
- English account-lock query -> the same document and source, score `0.772`.
- The model/tokenizer request used URLs pinned to Spring AI release tag `v2.0.0`.
- With threshold `0.95`, the same query produced `RAG_REJECT`, 0 retrieved citations, 0 tool calls, and 0 pending actions.
- Every temporary process was stopped and its port released.

## Boundary

`SimpleVectorStore` is explicitly not production infrastructure. It is appropriate only for this local prototype and automated tests. This is not a production indexing pipeline: there is no durable chunk table, pgvector, reranker, index version migration, or distributed refresh. A production-oriented follow-up would place those concerns behind the same `VectorStoreRetriever` boundary.
