# TicketOpsAgent Vector RAG Implementation Plan

> **For agentic workers:** Implement task-by-task with TDD. Keep the user-facing deterministic and write-safety boundaries unchanged.

**Goal:** Replace keyword/table SOP selection with embedding-based Spring AI vector retrieval, source citations, and low-similarity refusal while preserving offline reproducibility and adding a real local ONNX profile.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, `EmbeddingModel`, `SimpleVectorStore`, `VectorStoreRetriever`, ONNX Transformers, JUnit 5, AssertJ.

---

### Task 1: Add failing vector retrieval contracts

**Files:**
- Create: `src/test/java/com/tzq/ticketops/rag/OfflineFeatureHashEmbeddingModelTest.java`
- Create: `src/test/java/com/tzq/ticketops/rag/SopVectorRetrievalTest.java`
- Modify: `src/test/java/com/tzq/ticketops/rag/SopSearchServicePersistenceTest.java`
- Modify: `src/test/java/com/tzq/ticketops/agent/AgentOrchestratorTest.java`

- [x] Verify embeddings are numeric, normalized, deterministic, and query-dependent.
- [x] Verify Chinese and English account-lock text retrieves `SOP-ACCOUNT-LOCKED` with its real source.
- [x] Verify unrelated text returns `LOW_SIMILARITY` rather than a fallback document.
- [x] Verify database document changes refresh the vector index.
- [x] Verify low confidence prevents tool calls and pending actions.
- [x] Run targeted tests and confirm RED because vector components/results do not exist.

### Task 2: Add Spring AI vector and ONNX dependencies

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/resources/application-onnx.yml`

- [x] Add `spring-ai-vector-store` under the existing BOM and place the large `spring-ai-starter-model-transformers` runtime behind Maven profile `onnx`.
- [x] Keep the default provider offline and set explicit top-k/threshold configuration.
- [x] Add an `onnx` profile enabling the transformers embedding model and local cache.
- [x] Compile and inspect the resolved dependency tree.

### Task 3: Implement document source, embeddings, and vector index

**Files:**
- Modify: `src/main/java/com/tzq/ticketops/rag/SopDocument.java`
- Create: `src/main/java/com/tzq/ticketops/rag/SopDocumentSource.java`
- Create: `src/main/java/com/tzq/ticketops/rag/DefaultSopDocuments.java`
- Create: `src/main/java/com/tzq/ticketops/rag/JdbcSopDocumentSource.java`
- Create: `src/main/java/com/tzq/ticketops/rag/OfflineFeatureHashEmbeddingModel.java`
- Create: `src/main/java/com/tzq/ticketops/rag/SopVectorStoreConfiguration.java`
- Create: `src/main/java/com/tzq/ticketops/rag/RefreshingSopVectorStoreRetriever.java`

- [x] Implement deterministic word/CJK n-gram feature embeddings.
- [x] Build `SimpleVectorStore` from the selected `EmbeddingModel`.
- [x] Convert SOP rows to Spring AI `Document` with ID/title/source metadata.
- [x] Refresh only when the source fingerprint changes.
- [x] Expose only `VectorStoreRetriever` to the search service.

### Task 4: Replace keyword search and enforce refusal

**Files:**
- Create: `src/main/java/com/tzq/ticketops/rag/SopSearchResult.java`
- Create: `src/main/java/com/tzq/ticketops/rag/SopSearchStatus.java`
- Modify: `src/main/java/com/tzq/ticketops/rag/SopSearchService.java`
- Modify: `src/main/java/com/tzq/ticketops/agent/AgentOrchestrator.java`

- [x] Search top-k 1 through `VectorStoreRetriever`.
- [x] Return citation and real score for accepted candidates.
- [x] Return `LOW_SIMILARITY` / `NO_DOCUMENTS` with observed evidence for rejection.
- [x] Add source, score, threshold, and provider to accepted trace.
- [x] Stop before tools/pending actions on rejected retrieval.
- [x] Run targeted tests and confirm GREEN.

### Task 5: Update mock SOP content, docs, and eval evidence

**Files:**
- Modify: `src/main/resources/data.sql`
- Modify: `README.md`
- Modify: `src/test/java/com/tzq/ticketops/eval/ReadmePublicReadinessTest.java`
- Create: `docs/rag/vector-rag.md`

- [x] Make mock SOP content explicit enough for Chinese and English retrieval without query-side ID selection.
- [x] Document offline vs ONNX providers and the SimpleVectorStore boundary.
- [x] Document source citations and refusal behavior.
- [x] Update the verified test count only after the final suite passes.

### Task 6: Verify default and ONNX paths

- [x] Run targeted vector tests.
- [x] Run `mvn test` and `mvn package`.
- [x] Run `scripts\accept.ps1`.
- [x] Run default live backend and five-scenario smoke.
- [x] Run ONNX profile retrieval smoke and capture provider/ID/source/score/refusal evidence.
- [x] Run strict key-shaped scan and `git diff --check`.
- [x] Update README evidence and mark this plan complete.

### Task 7: Publish and record

- [ ] Commit, push, and create a focused PR.
- [ ] Review the full diff and merge only after evidence is complete.
- [ ] Re-run acceptance on `main`.
- [ ] Update the TicketOpsAgent project control, task queue, formal module note, Daily note, and reusable RAG knowledge note in Obsidian.
