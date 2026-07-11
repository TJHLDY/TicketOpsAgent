# Vector RAG

TicketOpsAgent retrieves mock IT support SOP documents through Spring AI `VectorStoreRetriever`. Retrieval is embedding-based, returns a source citation, and stops the workflow when the best score is below the configured threshold.

## Runtime flow

1. `JdbcSopDocumentSource` reads the current `sop_document` rows.
2. `RefreshingSopVectorStoreRetriever` compares the current rows with the indexed snapshot and refreshes the prototype index only when they change.
3. The selected `EmbeddingModel` converts SOP text and the rewritten query into numeric vectors.
4. Spring AI `SimpleVectorStore` performs cosine-similarity search.
5. `SopSearchService` returns the best document only when its score reaches the threshold.
6. Accepted retrieval produces `RAG_RETRIEVE`; rejected retrieval produces `RAG_REJECT` before any tool or pending action.

`SopSearchService` depends on the read-only `VectorStoreRetriever` interface. Mutable indexing is encapsulated inside the refreshing adapter.

The JDBC source fails closed: an empty `sop_document` table yields `NO_DOCUMENTS`; it never silently substitutes built-in documents. Built-in documents are used only by the explicit no-Spring offline factory for unit/demo construction.

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
- actual similarity score;
- configured threshold;
- embedding provider.

The user-facing `retrievedDocuments` field returns the same ID/title/source/score citation.

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

The vector tests cover bilingual account-lock retrieval, permission and MFA retrieval, unrelated-query refusal, database refresh, source citation, and the no-tool/no-pending-action refusal boundary.

Latest local ONNX smoke evidence:

- Chinese account-lock query -> `SOP-ACCOUNT-LOCKED`, source `mock-sop/account-locked.md`, provider `onnx`, score `0.568`.
- English account-lock query -> the same document and source, score `0.772`.
- The model/tokenizer request used URLs pinned to Spring AI release tag `v2.0.0`.
- With threshold `0.95`, the same query produced `RAG_REJECT`, 0 retrieved citations, 0 tool calls, and 0 pending actions.
- Every temporary process was stopped and its port released.

## Boundary

`SimpleVectorStore` is explicitly not production infrastructure. It is appropriate only for this local prototype and automated tests. The production-oriented follow-up is pgvector behind the same `VectorStoreRetriever` boundary, with schema/versioned indexing and operational monitoring.
