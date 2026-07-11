# SOP Document Chunking Implementation Plan

1. Add failing chunker contract tests.
   - Verify multiple chunks, deterministic IDs, parent metadata, sequential indexes, and totals.

2. Implement `SopDocumentChunker` with Spring AI `TokenTextSplitter`.
   - Keep fixed prototype settings and no new external dependency.

3. Add failing retrieval citation tests.
   - Insert a long database SOP whose relevant terms occur in a later chunk.
   - Require parent ID plus chunk ID/index/total in `SopReference`.

4. Integrate chunking into refreshing vector indexing.
   - Index chunks and track actual chunk IDs for deletion.
   - Preserve empty-source and database-refresh behavior.

5. Extend accepted RAG trace evidence.
   - Include chunk ID/index/total without storing chunk text.

6. Update README and vector RAG guide.
   - Document runtime flow, citation fields, settings, verification, and prototype boundary.

7. Verify and review.
   - Run targeted tests, full tests, acceptance, package, live scenarios, diff/secret/bidi scans, and independent Codex review.
   - Fix actionable findings with regression tests before opening the PR.
