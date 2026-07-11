# TicketOpsAgent

A Spring Boot + Spring AI backend prototype for enterprise IT account and permission tickets.

The project demonstrates deterministic ticket triage, embedding-based SOP retrieval, read-only tool calls, pending action review, audit traces, and DeepSeek shadow candidate evaluation with Java-side DTO parsing, validation, fallback, and acceptance reporting.

The current MVP verifies a controllable backend agent chain:

1. Create an `OPEN` ticket from requester/title/description.
2. Classify account and permission-related ticket text.
3. Embed the retrieval query and retrieve an SOP/FAQ source citation from a Spring AI vector store.
4. Validate a proposed tool intent through an allowlist, exact DTO schema, requester binding, category policy, and call budget before calling a read-only mock tool.
5. Generate an internal suggestion, user reply draft, pending action, and trace events.
6. Persist ticket, trace, tool call, pending action, internal suggestion, and reply draft evidence.
7. Verify the routing boundary with deterministic Eval cases and mock LLM shadow Eval cases.

## Current Status

- Java 21.
- Spring Boot 4.1.0.
- Spring AI 2.0.0 BOM with DeepSeek starter support.
- PostgreSQL Docker Compose profile for local persistence.
- H2 default profile for fast tests.
- 126 automated tests passing at the latest verification.
- Spring AI `VectorStoreRetriever` retrieval with source citations and low-similarity refusal.
- Offline feature-hash embedding for deterministic, key-free tests and demos.
- Optional local ONNX Transformers profile for neural embeddings without a cloud API key.
- Backend-owned `ReadOnlyToolExecutor` with exact schemas, requester identity binding, category policy, app-code normalization, and one-call budget.
- `AgentDecisionPort` boundary in place with deterministic routing plus DeepSeek shadow evaluation.
- DeepSeek shadow mode calls the model, parses a candidate `AgentDecision`, validates enums/tools/pending actions/confidence, and falls back to deterministic output on validation/API errors.
- Mock LLM shadow Eval runner covers 34 accepted, unsafe, invalid model-output, tool-argument, and pending-action mismatch cases without requiring a real API key.
- `scripts\accept.ps1` can optionally run a real DeepSeek shadow smoke check and record provider/model/prompt/schema/latency/fallback evidence.
- DeepSeek shadow evaluation is phase-closed for this spike: evaluable, rollback-safe, and reproducible enough for review and resume/interview material.
- Backend API productization is implemented: ticket detail/list, trace/tool call reads, pending action review, and eval report reads.
- Agent-generated internal suggestions and user reply drafts are persisted for read-only audit.
- Backend API error contract hardening is implemented: stable JSON error codes for missing resources, invalid requests, and already reviewed pending actions.
- Privacy-safe Micrometer metrics and local Actuator health/metrics diagnostics are implemented with bounded tags.
- No real enterprise system integration.

## Implemented MVP Scenarios

### Account Locked

Input example:

```text
I cannot sign in to the OA system. It says my account is locked. Please help me recover access.
```

Expected chain:

- `ACCOUNT_LOCKED`
- `P2`
- `NEEDS_APPROVAL`
- `SOP-ACCOUNT-LOCKED`
- `getAccountStatus`
- `LOCKED`
- `UNLOCK_ACCOUNT` pending action

### Permission Request

Input example:

```text
I cannot access CRM. It says I do not have permission. Please help me request access.
```

Expected chain:

- `PERMISSION_REQUEST`
- `P3`
- `NEEDS_APPROVAL`
- `SOP-PERMISSION-REQUEST`
- `getUserPermissions`
- `NONE`
- `GRANT_PERMISSION` pending action

## Current Boundary

- Uses mock SOP, account, and permission data only.
- Does not connect to LDAP, SSO, IAM, OA, or a real ticket system.
- Does not execute unlock, reset password, grant permission, or close-ticket operations.
- Write operations are represented only as `pending_action` rows.
- Default agent routing is deterministic.
- `ticketops.agent.mode=shadow` keeps deterministic user-facing output and records shadow decision traces.
- The `deepseek` profile enables a Spring AI-backed shadow decision service. Invalid or unsafe model output is recorded as `LLM_SHADOW_FAILED` and does not affect the user-facing response.
- SOP retrieval uses Spring AI `SimpleVectorStore` behind a read-only `VectorStoreRetriever`; `SimpleVectorStore` is a prototype/demo implementation, not production pgvector.
- The default offline feature-hash embedding is deterministic test/demo infrastructure, not a trained semantic model. The optional `onnx` profile uses local ONNX Transformers.
- Low-confidence retrieval stops before tool calls and pending actions and records `RAG_REJECT`.
- Tool intents are untrusted proposals. Rejected tool validation records `TOOL_REJECT` and creates zero successful tool calls and zero pending actions.

## Quick Start

No DeepSeek API key is required for the default local demo.

Run the test suite and acceptance gate:

```powershell
mvn test
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

Start the app and run the backend API demo:

```powershell
mvn spring-boot:run
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1 -BaseUrl http://localhost:8080
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-scenarios.ps1 -BaseUrl http://localhost:8080
```

Open the lightweight static demo console:

```text
http://localhost:8080/demo-console.html
```

Optional DeepSeek live smoke:

```powershell
$env:DEEPSEEK_API_KEY="<your key>"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1 -SkipMavenTest -IncludeLiveDeepSeek
```

Do not commit API keys. DeepSeek keys must be supplied through environment variables only.

## Vector RAG

The default path indexes the mock `sop_document` rows into Spring AI `SimpleVectorStore` and searches them through the read-only `VectorStoreRetriever` interface. Accepted results return the document ID, title, source citation, and actual similarity score. Retrieval traces also record the threshold and embedding provider.

Default offline mode is repeatable and key-free:

```yaml
ticketops:
  rag:
    embedding-provider: offline
    similarity-threshold: 0.30
    top-k: 1
```

The offline feature-hash embedding produces real numeric vectors for deterministic tests, but it is intentionally not presented as a trained semantic model. To use Spring AI's local neural embedding model, activate both the Maven and Spring `onnx` profiles:

```powershell
mvn -Ponnx spring-boot:run "-Dspring-boot.run.profiles=onnx"
```

The first ONNX run may download and cache model resources. It requires no embedding API key. Both providers use the same retrieval/result contract.

When the best score is below `ticketops.rag.similarity-threshold`, the Agent returns a human-transfer draft, writes `RAG_REJECT`, and creates no tool call or pending action. See [docs/rag/vector-rag.md](docs/rag/vector-rag.md) for design, commands, evidence, and the explicit not production boundary.

## Controlled Read-Only Tool Execution

Both user-facing read-only calls run through `ReadOnlyToolExecutor`; category handlers no longer invoke concrete mock tools directly. The executor owns the allowlist, exact argument sets, typed DTO conversion, requester identity binding, application-code normalization, category-to-tool policy, and invocation.

The default call budget is explicit:

The property `ticketops.tools.max-calls-per-request` is fixed at `1` for the current single-tool MVP. Startup rejects any other value instead of pretending to support a multi-tool loop.

```yaml
ticketops:
  tools:
    max-calls-per-request: 1
```

Successful requests record `TOOL_DECISION` followed by a validated `TOOL_CALL`. Unknown tools, requester mismatch, missing or extra arguments, unsupported applications, category mismatch, and budget overflow record `TOOL_REJECT`. Rejection returns a human-review response with zero successful tool calls and zero pending actions.

The tools remain mock-only database reads. No write tool is registered, and pending actions remain audit-only. See [docs/tools/controlled-tool-execution.md](docs/tools/controlled-tool-execution.md) for schemas, rejection reasons, verification, and boundaries.

Tool results also constrain later actions: an account-lock ticket creates `UNLOCK_ACCOUNT` only when `getAccountStatus` returns `LOCKED`. An `ACTIVE`, `MFA_REQUIRED`, or `UNKNOWN` result keeps the read evidence but creates no unlock pending action.

## Privacy-Safe Observability

The backend records aggregate Micrometer metrics for request duration/outcome, RAG status, controlled tool success/rejection, pending action proposals, and DeepSeek shadow outcomes. Metric tags are limited to enums, fixed outcomes, the two read-only tool names, and normalized embedding providers; requester IDs, ticket text, RAG content, tool arguments/results, and prompt and completion content remain disabled.

After starting the application and creating a ticket, inspect the local endpoints:

```powershell
Invoke-RestMethod http://127.0.0.1:8081/actuator/health
Invoke-RestMethod http://127.0.0.1:8081/actuator/metrics/ticketops.agent.request
Invoke-RestMethod http://127.0.0.1:8081/actuator/metrics/ticketops.rag.retrieval
Invoke-RestMethod http://127.0.0.1:8081/actuator/metrics/ticketops.tool.execution
```

Only `health`, `info`, and `metrics` are exposed on the separate management server. It defaults to `127.0.0.1:8081`, so the business port does not serve `/actuator/*` and remote hosts cannot connect to the management listener. `/actuator/env` is not exposed. No Prometheus, Grafana, or external tracing backend is added in this MVP; the Actuator metrics endpoint is for local diagnostics. See [docs/observability/privacy-safe-observability.md](docs/observability/privacy-safe-observability.md) for the metric contract and privacy boundary.

## Persisted Agent Drafts

Every completed Agent request stores two ordered `ticket_message` records: `INTERNAL_SUGGESTION` for the operator-facing handling suggestion and `USER_REPLY_DRAFT` for the user-facing reply draft. They are committed in the same transaction as trace, tool call, and pending action evidence.

Inspect them through the read-only endpoint:

```text
GET /api/tickets/{ticketId}/messages
```

No message is automatically sent. There is no message send, edit, delivery, or outbound integration endpoint in this MVP. See [docs/api/agent-draft-persistence.md](docs/api/agent-draft-persistence.md) for the storage contract and draft-only boundary.

## Scenario Acceptance

The core backend ticket flows are covered by `ScenarioAcceptanceTest`:
account lock, permission request, MFA issue, prompt injection rejection, and non-IT request rejection.

Run the scenario suite:

```powershell
mvn test "-Dtest=ScenarioAcceptanceTest"
```

See [docs/scenarios/scenario-playbook.md](docs/scenarios/scenario-playbook.md) for the accepted inputs, expected classifications, tool calls, pending actions, and boundaries.

## Scenario Demo Report

`scripts\demo-scenarios.ps1` replays the five accepted MVP scenarios against a running local app and writes:

- `target/scenario-acceptance/scenario-report.json`
- `target/scenario-acceptance/scenario-report.md`

The report summarizes `totalScenarios`, `passedScenarios`, `failedScenarios`, and per-scenario evidence such as category, risk level, retrieved SOP, read-only tool result, pending action, and `NOT_EXECUTED_MOCK_ONLY` execution status.
It also records a generated `runId` and the current run's `ticketIds`; the script reads evidence from the `ticketId` returned by `/api/agent/chat` so repeated local runs are not confused by older tickets.

Run without calling the server:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\demo-scenarios.ps1 -ShowPlan
```

See [docs/scenarios/scenario-report-guide.md](docs/scenarios/scenario-report-guide.md) for the report shape, review method, and boundaries. See [docs/scenarios/reproducibility-notes.md](docs/scenarios/reproducibility-notes.md) for repeat-run checks.

## Architecture Overview

```text
/api/agent/chat
      |
      v
Deterministic Decision Service  ---> user-facing response
      |
      +--> Query Rewrite / Embedding / VectorStoreRetriever
      +--> ReadOnlyToolExecutor / Read-only Tools
      +--> Pending Action Proposal
      +--> Trace / Tool Log / Pending Action
      |
      +--> DeepSeek Shadow Candidate
              |
              v
        DTO / Parser / Validator
              |
        LLM_SHADOW or LLM_SHADOW_FAILED
              |
        fallback deterministic
```

The deterministic path remains the user-facing main flow. The DeepSeek path is a shadow candidate path used for comparison, traceability, and evaluation.

## Validation Evidence

Latest local validation:

- `mvn test`: 126 tests PASS
- `scripts\accept.ps1`: PASS
- Secret scan: PASS
- Shadow eval: 34 cases
- Safety cases: 9/9
- Trace audit: 34/34
- User-visible changed count: 0
- Optional live smoke: supported
- Demo script: 7-step backend flow PASS
- Scenario demo report: 5 scenarios PASS
- Vector retrieval contracts: bilingual retrieval, database refresh, source citation, and low-similarity refusal PASS
- Local ONNX smoke: Chinese account-lock query retrieved `SOP-ACCOUNT-LOCKED` from `mock-sop/account-locked.md` with provider `onnx` and score `0.568`
- ONNX refusal smoke at threshold `0.95`: `RAG_REJECT`, 0 tool calls, and 0 pending actions
- Controlled tool execution: allowlist, exact schemas, requester binding, category policy, normalization, one-call budget, and fail-closed rejection PASS

These numbers are local validation evidence, not a production SLA.

## Lightweight Demo Console

Start the app:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080/demo-console.html
```

The console demonstrates ticket creation through `/api/agent/chat`, ticket detail lookup, trace timeline, read-only tool call evidence, pending action review with `NOT_EXECUTED_MOCK_ONLY`, and eval report summary.

The console is a local static demo page only. It does not add login, RBAC, real enterprise integrations, real execution, LLM main decisioning, hybrid mode, or production RAG.

## Demo Data

The local demo uses mock users, mock account status, mock permissions, and mock SOP documents. The default demo script uses `mock-user-001`, whose account status is seeded as `LOCKED`.

Pending action review updates only local audit state and never executes real enterprise operations. Review responses keep `executionStatus=NOT_EXECUTED_MOCK_ONLY`.

## What This Project Does Not Claim

This repository is not a production AI Agent or production ITSM system. It does not claim:

- real enterprise system integration
- LDAP / SSO / IAM / OA integration
- real account unlock
- real password reset
- real permission grant
- real dispatch or close-ticket operation
- LLM main decisioning
- LLM-driven real tool execution
- production RAG / pgvector
- production approval workflow

## Roadmap

- [x] Backend Agent spike
- [x] DeepSeek shadow evaluator
- [x] Shadow eval and acceptance report
- [x] Backend API productization
- [x] Backend API demo script
- [x] Public portfolio README hardening
- [x] Lightweight static demo console
- [x] API error contract hardening
- [x] Scenario acceptance suite
- [x] Scenario demo script and report summary
- [x] Spring AI vector RAG with source citations and low-similarity refusal
- [x] Controlled read-only tool execution with validation, budget, and rejection traces
- [x] Persist internal suggestions and user reply drafts as read-only audit evidence
- [x] Privacy-safe Micrometer metrics and local Actuator diagnostics

## Documentation

- [Acceptance review](docs/eval/acceptance-review.md): commands and expected gates for the current shadow acceptance report.
- [DeepSeek shadow stage summary](docs/eval/deepseek-shadow-stage-summary.md): phase-close evidence, metrics, boundaries, and non-goals.
- [Backend API productization guide](docs/api/backend-api-productization.md): local demo flow for ticket, trace, tool call, pending action review, and eval report APIs.
- [Scenario acceptance playbook](docs/scenarios/scenario-playbook.md): accepted business scenarios, expected evidence, and non-goals.
- [Scenario report guide](docs/scenarios/scenario-report-guide.md): local script, generated report shape, and acceptance review method.
- [Vector RAG guide](docs/rag/vector-rag.md): embedding providers, read-only retrieval, refusal behavior, and prototype boundary.
- [Controlled tool execution guide](docs/tools/controlled-tool-execution.md): allowlist, typed schemas, requester binding, budget, traces, and fail-closed behavior.
- [Privacy-safe observability guide](docs/observability/privacy-safe-observability.md): bounded metrics, local Actuator inspection, sensitive-content defaults, and non-goals.
- [Agent draft persistence guide](docs/api/agent-draft-persistence.md): atomic storage, read API, and draft-only boundary.
- [Interview notes](docs/interview/ticketops-interview-notes.md): resume-safe wording, STAR story, trade-offs, and likely interviewer questions.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

## Run Tests

```powershell
mvn test
```

Run only the lightweight Eval cases:

```powershell
mvn test "-Dtest=MinimalEvalCaseTest"
```

Run only the mock LLM shadow Eval runner:

```powershell
mvn test "-Dtest=LlmShadowEvalRunnerTest"
```

The mock shadow Eval writes `target/agent-eval/llm-shadow-eval.json`. The latest local run produced:

- `totalCases`: 34
- `parseSuccessCount`: 31
- `validationSuccessCount`: 11
- `fallbackCount`: 23
- `safetyPassCount`: 9 of 9
- `traceAuditPassCount`: 34 of 34
- `userVisibleChangedCount`: 0

Run the one-command acceptance gate:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

The acceptance script runs `mvn test`, scans for committed key-shaped secrets, reads the mock shadow Eval JSON, and writes `target/agent-eval/acceptance-report.md`.

Run the same gate with optional live DeepSeek smoke cases:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1 -IncludeLiveDeepSeek
```

Live smoke stays off by default. With `-IncludeLiveDeepSeek` and no `DEEPSEEK_API_KEY`, the report marks `Live DeepSeek: SKIPPED`. With a key, the script starts the app on a temporary port, calls three smoke cases, records `provider`, `model`, `promptVersion`, `schemaVersion`, per-case latency, risk levels, and fallback reason, then stops the local app process. `userRiskLevel` is the deterministic baseline and `shadowRiskLevel` is the LLM shadow candidate. The key itself is never written to the report.

## Agent Mode

Default mode is deterministic:

```yaml
ticketops:
  agent:
    mode: deterministic
```

Run in shadow mode from PowerShell:

```powershell
$env:TICKETOPS_AGENT_MODE='shadow'
mvn spring-boot:run
```

Run with the DeepSeek shadow profile:

```powershell
$env:DEEPSEEK_API_KEY='<your key>'
mvn spring-boot:run "-Dspring-boot.run.profiles=deepseek"
```

Current shadow behavior is intentionally conservative: deterministic output is still returned to the caller. The DeepSeek result is treated only as a candidate decision and must pass Java-side validation before being recorded as a comparable shadow decision.

Shadow trace details include audit fields such as `llm_status`, `fallback_reason`, `fallback_to`, `provider`, `model`, `prompt_version`, `schema_version`, `latency_ms`, `validation_errors`, `final_decision_source`, and `user_visible_changed`. Latest live smoke through `scripts\accept.ps1 -IncludeLiveDeepSeek` reached the real shadow path while the user-facing response still came from the deterministic baseline.

## Database

The default profile uses in-memory H2 so tests and local spikes start quickly.

Start the PostgreSQL dependency:

```powershell
docker compose up -d postgres
```

Run the app against PostgreSQL:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=postgres"
```

## Local API

Start the application:

```powershell
mvn spring-boot:run
```

Account locked request:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/agent/chat `
  -ContentType 'application/json' `
  -Body '{"requesterId":"mock-user-001","title":"OA login failed","description":"I cannot sign in to the OA system. It says my account is locked. Please help me recover access."}'
```

Permission request:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/agent/chat `
  -ContentType 'application/json' `
  -Body '{"requesterId":"mock-user-005","title":"CRM permission request","description":"I cannot access CRM. It says I do not have permission. Please help me request access."}'
```

Each request persists the ticket and execution evidence to `ticket`, `agent_trace`, `tool_call_log`, and `pending_action`.

### Backend Audit APIs

After creating a ticket through `/api/agent/chat`, use the returned `ticketId` to inspect the execution evidence:

```powershell
Invoke-RestMethod http://localhost:8080/api/tickets
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/trace
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/tool-calls
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/pending-actions
Invoke-RestMethod http://localhost:8080/api/eval/reports/latest
```

The eval report endpoint is for local demo and development review only. It is not a production monitoring API.

Pending action review is audit-only and does not execute real account or permission changes:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/pending-actions/{actionId}/approve `
  -ContentType 'application/json' `
  -Body '{"reviewerId":"admin-mock","reviewComment":"Approved for audit demo"}'

Invoke-RestMethod -Method Post http://localhost:8080/api/pending-actions/{actionId}/reject `
  -ContentType 'application/json' `
  -Body '{"reviewerId":"admin-mock","reviewComment":"Rejected for audit demo"}'
```

The review response always keeps `executionStatus` at `NOT_EXECUTED_MOCK_ONLY`.

Run the end-to-end backend API demo script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1
```

Run the five-scenario local report script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-scenarios.ps1
```

## Planned Next Phase

The backend API and local demo contracts are stable enough to begin closing the remaining AI implementation gaps in separate, verifiable stages:

1. Extend eval coverage and produce a consolidated backend completion report for structured output, tool selection, prompt injection, excessive agency, retrieval quality, fallback, and observability.
2. Replace `SimpleVectorStore` with pgvector only in a later production-oriented persistence stage.
3. Build the full frontend only after the backend AI contracts and runtime evidence are stable.

These items are planned, not implemented. The current default remains deterministic, vector retrieval remains a prototype implementation, and no real enterprise write operation is executed.

DeepSeek keys must be supplied through environment variables, never committed.
