# TicketOpsAgent Vector RAG Design

## Context

PR #9 established Java 21, Spring Boot 4.1.0, and Spring AI 2.0.0. The current `SopSearchService` still selects documents with hard-coded keyword branches and always returns similarity `0.92`, so the repository cannot honestly claim embedding-based retrieval or low-confidence refusal yet.

The next stage must replace that implementation without coupling retrieval correctness to a cloud API key, production vector database, or LLM-controlled tool execution.

## Goals

- Compute document and query embeddings instead of selecting a document by ID-specific keyword branches.
- Store and search SOP documents through Spring AI `VectorStore` / `VectorStoreRetriever`.
- Keep the search-facing dependency read-only and encapsulate index mutation in an indexing adapter.
- Return stable source citations (`id`, `title`, `source`, score).
- Refuse low-similarity results before any tool call or pending action is created.
- Record accepted/rejected retrieval evidence with score, threshold, source, and embedding provider.
- Keep the default test and demo path deterministic, offline, and key-free.
- Provide a separate local ONNX Transformers profile and obtain a real-model smoke result.

## Non-goals

- Production pgvector, schema migration, or high-availability indexing.
- Passing retrieved SOP text into an LLM main path.
- LLM main/hybrid routing or LLM-controlled writes.
- Real enterprise systems or real account/permission mutation.
- Claiming `SimpleVectorStore` is production-ready.

## Architecture

### Document source

`SopDocumentSource` loads the current `sop_document` rows. A static source supplies the same five mock documents for direct unit construction. Document text remains the source of truth; bilingual terms may be included in mock content so both Chinese and English tickets are testable.

### Embedding providers

The default `offline` provider uses a deterministic feature-hash embedding model. It creates normalized numeric vectors from word and CJK n-gram features and supports repeatable offline tests. It is a retrieval test/demo embedding, not a claim of production semantic quality.

The `onnx` profile enables Spring AI `TransformersEmbeddingModel`, which runs an ONNX sentence-transformer locally and requires no API key. Its first use may download and cache model resources.

### Vector index and least privilege

`RefreshingSopVectorStoreRetriever` owns the mutable `VectorStore`, fingerprints the source rows, and refreshes the small prototype index only when rows change. It exposes only `VectorStoreRetriever` to `SopSearchService`, following Spring AI's read-only least-privilege boundary.

`SimpleVectorStore` is intentionally limited to the MVP/demo profile. A later PR may replace it with pgvector behind the same retrieval interface.

### Retrieval result

`SopSearchService.search(text)` returns `SopSearchResult` with:

- accepted citation, if any;
- best observed score;
- configured threshold;
- embedding provider;
- status (`ACCEPTED`, `LOW_SIMILARITY`, or `NO_DOCUMENTS`).

Search requests use top-k 1 and retrieve the best candidate. The application compares the returned score with the configured threshold so rejected traces can retain both the observed score and threshold.

### Orchestrator safety

For account-lock and permission flows, retrieval runs before tools. If retrieval is rejected:

- no read-only tool is called;
- no pending action is created;
- the user receives a transfer-to-human draft;
- trace contains `RAG_REJECT` with reason, score, threshold, and provider.

Accepted retrieval keeps `RAG_RETRIEVE`, adds source/threshold/provider evidence, then continues the existing deterministic tool and pending-action flow.

Retrieved document content does not control tool selection, arguments, risk classification, or pending actions.

## Verification

- Unit tests for deterministic vector shape and repeatability.
- Retrieval tests for Chinese/English account lock, permission, MFA, source citation, and unrelated-query rejection.
- Persistence test proving database rows are indexed and refreshed.
- Orchestrator test proving low confidence blocks tools and pending actions.
- Existing 81-test regression suite.
- Acceptance gate and strict secret scan.
- Default live scenario report.
- ONNX profile smoke that reports provider, retrieved ID/source/score, and low-similarity behavior without exposing secrets.

## Official references

- Spring AI VectorStore and read-only VectorStoreRetriever: https://docs.spring.io/spring-ai/reference/api/vectordbs.html
- Spring AI Embedding Model API: https://docs.spring.io/spring-ai/reference/api/embeddings.html
- Spring AI Transformers ONNX embeddings: https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html
- Spring AI SimpleVectorStore API and prototype-only warning: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/vectorstore/SimpleVectorStore.html
- OWASP Top 10 for Agentic Applications 2026: https://genai.owasp.org/resource/owasp-top-10-for-agentic-applications-for-2026/
