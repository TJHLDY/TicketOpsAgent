# Privacy-Safe Observability Design

## Goal

Add locally inspectable, low-cardinality observability for the TicketOpsAgent pipeline without exporting ticket text, requester identifiers, tool arguments, RAG query content, retrieved document content, prompts, completions, or model responses.

## Context

PR #11 established a backend-controlled trust boundary for read-only tool execution. The next engineering gap is operational evidence: a reviewer can inspect trace rows, but cannot yet answer aggregate questions such as how many requests were rejected by RAG, which allowlisted tool outcomes occurred, or how long the agent pipeline took.

Spring Boot 4.1 auto-configures Micrometer through Actuator, and Spring AI 2.0 emits observations for ChatClient, ChatModel, and VectorStore operations. Spring AI deliberately keeps prompt/completion, tool arguments/results, and vector query/response content disabled by default because those values may be sensitive.

## Considered Approaches

### 1. Parse persisted trace text into metrics

This would avoid changing the orchestrator, but it would make metric correctness depend on human-readable trace string formatting. It is rejected because a wording change could silently break aggregation.

### 2. Call `MeterRegistry` directly throughout business methods

This is small, but spreads metric names, tag policy, and privacy decisions across the pipeline. It also makes non-Spring unit construction awkward.

### 3. Dedicated telemetry boundary backed by Micrometer

This is the selected approach. `AgentTelemetry` owns metric names, bounded tag normalization, timing, and no-op behavior. `AgentController` owns the request timer so persistence failures cannot be reported as successful API requests, while `AgentOrchestrator` reports typed RAG, tool, pending-action, and shadow outcomes at the points where they become known. Spring wiring uses Micrometer; static test factories use the no-op implementation.

## Architecture

`AgentTelemetry` is a narrow application port. Its Micrometer implementation records:

- `ticketops.agent.request`: timer tagged by bounded `outcome`, `category`, and `risk`.
- `ticketops.rag.retrieval`: counter tagged by `status` and normalized `provider`.
- `ticketops.tool.execution`: counter tagged by allowlisted `tool`, `outcome`, and rejection `reason`.
- `ticketops.pending.action`: counter tagged by known pending-action `type`.
- `ticketops.shadow.decision`: counter tagged by bounded `status` and `fallback`.

The request timer wraps ticket creation, deterministic Agent processing, decision-summary update, and audit-log persistence. RAG and tool counters are emitted at the actual decision boundaries, not inferred from response text. Pending-action counters are emitted only after the response has a concrete audit-only proposal.

Actuator exposes only `health`, `info`, and `metrics`. No Prometheus registry, tracing backend, dashboard, or production alerting stack is added in this phase.

## Cardinality And Privacy Contract

Allowed tag values are enums, fixed outcomes, a two-item tool allowlist, and normalized provider buckets (`offline`, `onnx`, `other`). Unknown or null values collapse to `unknown` or `other`.

The following values are forbidden from metric names, descriptions, tags, and measurements:

- requester ID, ticket ID, title, or description;
- SOP query, document ID, title, source, or content;
- tool arguments or tool result summaries;
- prompt, completion, model response, or authorization data;
- exception messages.

Explicit configuration keeps all Spring AI sensitive-content observation switches false even though false is already the framework default. This makes the repository's privacy posture reviewable and regression-testable.

## Error Handling

Expected policy, RAG, and tool rejections are recorded as bounded outcomes rather than application errors. Unexpected runtime exceptions record `outcome=error` on the agent timer and are rethrown unchanged. Telemetry must never change the user-facing response or create a pending action.

## Verification

Tests will use `SimpleMeterRegistry` and assert counts/timers for successful account-lock, RAG rejection, tool rejection, pending action, and shadow fallback paths. A contract test will inspect all `ticketops.*` meter IDs and prove that supplied sensitive marker values do not appear in names, tags, descriptions, or base units.

A Spring Boot web test will prove `/actuator/health` and `/actuator/metrics/ticketops.agent.request` are available while unrelated actuator endpoints remain unexposed. Artifact tests will pin the explicit Spring AI sensitive-content settings and documentation. Full Maven tests, package, acceptance, live scenarios, metrics endpoint smoke, secret scan, and diff checks complete the phase.

## Boundary

This phase does not add Prometheus, Grafana, OpenTelemetry export, distributed tracing infrastructure, production SLOs, raw prompt logging, raw tool-argument logging, LLM main/hybrid mode, real enterprise integrations, or real write execution.
