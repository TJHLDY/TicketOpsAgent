# SOP Document Chunking Design

## Context

TicketOpsAgent currently maps each `sop_document` row to one Spring AI `Document`. Retrieval is vector-based, but the index has no document chunking and citations cannot identify the matched section of a longer SOP.

## Goal

Use Spring AI `TokenTextSplitter` to create stable, metadata-rich SOP chunks while preserving current retrieval, refusal, source citation, and index-refresh behavior.

## Design

### Chunk construction

- Add a package-owned `SopDocumentChunker` backed by Spring AI `TokenTextSplitter`.
- Split `title + content` with a fixed prototype token budget.
- Rebuild splitter output with deterministic IDs: `{sopId}#chunk-{zeroBasedIndex}`.
- Preserve parent SOP ID, title, source, chunk index, and total chunk count in metadata.
- Short SOPs remain one chunk with index `0` and total `1`.

### Refresh behavior

- `RefreshingSopVectorStoreRetriever` indexes chunks instead of whole rows.
- Track and delete actual chunk IDs when the JDBC snapshot changes.
- Keep the existing fail-closed behavior for an empty SOP table.

### Citation contract

- Keep `SopReference.id` as the parent SOP ID for compatibility.
- Add `chunkId`, `chunkIndex`, and `totalChunks`.
- Add the same fields to accepted `RAG_RETRIEVE` traces.
- Continue returning title, source, actual similarity, threshold, and embedding provider.

## Safety And Scope

- Chunk text remains retrieval context only and cannot choose categories, tool names, arguments, or pending actions.
- No pgvector, production indexing pipeline, reranker, multi-document synthesis, LLM main mode, or real enterprise integration.
- No schema migration is required because chunks are derived index artifacts.

## Acceptance

- A long SOP creates multiple deterministic chunks with complete metadata.
- A query matching a later section returns the parent SOP citation plus the matched chunk identity.
- Refresh deletes stale chunk IDs when database documents change.
- Existing short-document, low-similarity, scenario, tool, and pending-action behavior remains green.
- Full acceptance, live scenarios, strict scans, and independent review pass.

