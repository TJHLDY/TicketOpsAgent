# Privacy-Safe Observability

TicketOpsAgent exposes bounded Micrometer metrics for local diagnostics while keeping user, retrieval, tool, and model content out of metric identifiers and tags.

## Metrics

| Metric | Type | Low-cardinality tags | Meaning |
| --- | --- | --- | --- |
| `ticketops.agent.request` | Timer | `outcome`, `category`, `risk` | End-to-end API request count and duration, including audit persistence |
| `ticketops.rag.retrieval` | Counter | `status`, `provider` | Accepted, low-similarity, and empty-document retrieval outcomes |
| `ticketops.tool.execution` | Counter | `tool`, `outcome`, `reason` | Validated read-only calls and fail-closed rejection reasons |
| `ticketops.pending.action` | Counter | `type` | Audit-only pending action proposals |
| `ticketops.shadow.decision` | Counter | `outcome` | Accepted, fallback, or skipped shadow candidates |

The low-cardinality values are enums, fixed outcomes, the two read-only tool names, and the bounded provider values `offline`, `onnx`, and `other`. An unknown tool or provider is recorded as `other`; untrusted text is never used as a tag value.

## Local Inspection

Start the application and create at least one Agent request. Then inspect the health and metrics endpoints:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/metrics
Invoke-RestMethod http://localhost:8080/actuator/metrics/ticketops.agent.request
Invoke-RestMethod http://localhost:8080/actuator/metrics/ticketops.rag.retrieval
Invoke-RestMethod http://localhost:8080/actuator/metrics/ticketops.tool.execution
```

Only `health`, `info`, and `metrics` are exposed over HTTP. For example, `/actuator/env` remains unavailable. The metrics endpoint is a local diagnostics surface, not a production metrics backend.

## Privacy Contract

Metrics do not contain:

- requester ID, ticket ID, title, or description;
- SOP query, document identity, source, or document content;
- tool arguments, user ID, app code, or result summary;
- prompt, completion, model response, authorization data, or exception message.

Spring AI can observe ChatClient, ChatModel, tool-calling, and VectorStore operations. Its prompt/completion, tool arguments/results, and vector query/response content are sensitive and remain explicitly disabled in `application.yml`.

The trace and audit APIs still contain scenario evidence needed for this local prototype. They are separate from aggregate metrics and are not claimed to be a production privacy or retention design.

## Verification

`MicrometerAgentTelemetryTest` verifies metric names, counts, tags, one-shot request completion, normalization of untrusted values, and absence of a supplied sensitive marker from meter metadata.

`AgentOrchestratorTelemetryTest` verifies successful requests, RAG rejection, tool rejection, pending action creation, and shadow fallback against the real orchestrator path. `ObservabilityEndpointTest` verifies the exposed endpoint allowlist.

## Boundary

No Prometheus, Grafana, or external tracing backend is installed. This phase does not define production dashboards, alerts, SLOs, retention, access control, or distributed-tracing export. It does not enable sensitive Spring AI content observations.
