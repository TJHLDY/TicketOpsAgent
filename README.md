# TicketOpsAgent

TicketOpsAgent is a Spring Boot + Spring AI backend spike for enterprise IT account and permission tickets.

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
- 46 automated tests passing at the latest verification.
- `AgentDecisionPort` boundary in place with deterministic routing plus DeepSeek shadow evaluation.
- DeepSeek shadow mode calls the model, parses a candidate `AgentDecision`, validates enums/tools/pending actions/confidence, and falls back to deterministic output on validation/API errors.
- Mock LLM shadow Eval runner covers 34 accepted, unsafe, invalid model-output, tool-argument, and pending-action mismatch cases without requiring a real API key.
- `scripts\accept.ps1` can optionally run a real DeepSeek shadow smoke check and record provider/model/prompt/schema/latency/fallback evidence.
- DeepSeek shadow evaluation is phase-closed for this spike: evaluable, rollback-safe, and reproducible enough for review and resume/interview material.
- No real enterprise system integration.

## Implemented MVP Scenarios

### Account Locked

Input example:

```text
我登录 OA 系统失败，提示账号已锁定，帮我恢复一下。
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
我访问 CRM 提示无权访问，请帮我申请权限。
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

## Documentation

- [Acceptance review](docs/eval/acceptance-review.md): commands and expected gates for the current shadow acceptance report.
- [DeepSeek shadow stage summary](docs/eval/deepseek-shadow-stage-summary.md): phase-close evidence, metrics, boundaries, and non-goals.
- [Interview notes](docs/interview/ticketops-interview-notes.md): resume-safe wording, STAR story, trade-offs, and likely interviewer questions.

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
  -Body '{"requesterId":"mock-user-001","title":"OA 登录失败","description":"我登录 OA 系统失败，提示账号已锁定，帮我恢复一下。"}'
```

Permission request:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/agent/chat `
  -ContentType 'application/json' `
  -Body '{"requesterId":"mock-user-005","title":"CRM 权限申请","description":"我访问 CRM 提示无权访问，请帮我申请权限。"}'
```

Each request persists the ticket and execution evidence to `ticket`, `agent_trace`, `tool_call_log`, and `pending_action`.

## Planned Next Phase

The DeepSeek shadow stage is closed for this spike. The next phase should not add feature scope by default. Keep the deterministic baseline and use the acceptance report as the review artifact.

Recommended next work:

1. Prepare resume and interview material from the verified shadow evidence.
2. Merge or tag the current shadow-eval state after acceptance remains green.
3. Only consider `hybrid`, `llm`, pgvector, frontend, or real enterprise integrations in a separate phase with a new acceptance plan.

DeepSeek keys must be supplied through environment variables, never committed.
