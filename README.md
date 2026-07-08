# TicketOpsAgent

A Spring Boot + Spring AI backend prototype for enterprise IT account and permission tickets.

The project demonstrates deterministic ticket triage, SOP retrieval, read-only tool calls, pending action review, audit traces, and DeepSeek shadow candidate evaluation with Java-side DTO parsing, validation, fallback, and acceptance reporting.

The current MVP verifies a controllable backend agent chain:

1. Create an `OPEN` ticket from requester/title/description.
2. Classify account and permission-related ticket text.
3. Retrieve an SOP/FAQ reference from `sop_document`.
4. Call read-only mock tools backed by database tables.
5. Generate an internal suggestion, user reply draft, pending action, and trace events.
6. Persist ticket, trace, tool call, and pending action evidence.
7. Verify the routing boundary with deterministic Eval cases and mock LLM shadow Eval cases.

## Current Status

- Spring Boot 3.5.15.
- Spring AI 1.1.8 BOM with DeepSeek starter support.
- PostgreSQL Docker Compose profile for local persistence.
- H2 default profile for fast tests.
- 55 automated tests passing at the latest verification.
- `AgentDecisionPort` boundary in place with deterministic routing plus DeepSeek shadow evaluation.
- DeepSeek shadow mode calls the model, parses a candidate `AgentDecision`, validates enums/tools/pending actions/confidence, and falls back to deterministic output on validation/API errors.
- Mock LLM shadow Eval runner covers 34 accepted, unsafe, invalid model-output, tool-argument, and pending-action mismatch cases without requiring a real API key.
- `scripts\accept.ps1` can optionally run a real DeepSeek shadow smoke check and record provider/model/prompt/schema/latency/fallback evidence.
- DeepSeek shadow evaluation is phase-closed for this spike: evaluable, rollback-safe, and reproducible enough for review and resume/interview material.
- Backend API productization is implemented: ticket detail/list, trace/tool call reads, pending action review, and eval report reads.
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
- Current SOP retrieval is keyword/table driven, not vector RAG.

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
```

Optional DeepSeek live smoke:

```powershell
$env:DEEPSEEK_API_KEY="<your key>"
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1 -SkipMavenTest -IncludeLiveDeepSeek
```

Do not commit API keys. DeepSeek keys must be supplied through environment variables only.

## Architecture Overview

```text
/api/agent/chat
      |
      v
Deterministic Decision Service  ---> user-facing response
      |
      +--> SOP Retrieval
      +--> Read-only Tools
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

Latest local validation before public release:

- `mvn test`: 55 tests PASS
- `scripts\accept.ps1`: PASS
- Secret scan: PASS
- Shadow eval: 34 cases
- Safety cases: 9/9
- Trace audit: 34/34
- User-visible changed count: 0
- Optional live smoke: supported
- Demo script: 7-step backend flow PASS

These numbers are local validation evidence, not a production SLA.

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
- [ ] Lightweight static demo console
- [ ] Optional API error contract hardening

## Documentation

- [Acceptance review](docs/eval/acceptance-review.md): commands and expected gates for the current shadow acceptance report.
- [DeepSeek shadow stage summary](docs/eval/deepseek-shadow-stage-summary.md): phase-close evidence, metrics, boundaries, and non-goals.
- [Backend API productization guide](docs/api/backend-api-productization.md): local demo flow for ticket, trace, tool call, pending action review, and eval report APIs.
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
- `validationSuccessCount`: 12
- `fallbackCount`: 22
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

After creating a ticket through `/api/agent/chat`, use the ticket id from the database or ticket list API to inspect the execution evidence:

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

## Planned Next Phase

The current backend phase focuses on API productization, not new AI scope.

Recommended next work:

1. Keep backend API productization small: ticket query, trace/tool call query, pending action review, eval report query.
2. Run `scripts\accept.ps1` after each backend API checkpoint.
3. Build the frontend only after these read/review APIs are stable.
4. Only consider `hybrid`, `llm`, pgvector, or real enterprise integrations in a separate phase with a new acceptance plan.

DeepSeek keys must be supplied through environment variables, never committed.
