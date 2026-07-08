# TicketOpsAgent

TicketOpsAgent is a Spring Boot + Spring AI backend spike for enterprise IT account and permission tickets.

The current MVP verifies a controllable backend agent chain:

1. Create an `OPEN` ticket from requester/title/description.
2. Classify account and permission-related ticket text.
3. Retrieve an SOP/FAQ reference from `sop_document`.
4. Call read-only mock tools backed by database tables.
5. Generate an internal suggestion, user reply draft, pending action, and trace events.
6. Persist ticket, trace, tool call, and pending action evidence.
7. Verify the routing boundary with 15 Eval cases and automated tests.

## Current Status

- Spring Boot 3.5.15.
- Spring AI 1.1.8 BOM with DeepSeek starter support.
- PostgreSQL Docker Compose profile for local persistence.
- H2 default profile for fast tests.
- 41 automated tests passing at the latest verification.
- `AgentDecisionPort` boundary in place with deterministic routing plus DeepSeek shadow evaluation.
- DeepSeek shadow mode calls the model, parses a candidate `AgentDecision`, validates enums/tools/pending actions/confidence, and falls back to deterministic output on validation/API errors.
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

## Run Tests

```powershell
mvn test
```

Run only the lightweight Eval cases:

```powershell
mvn test "-Dtest=MinimalEvalCaseTest"
```

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

Latest live check with the `deepseek` profile started the application and reached the real shadow path. Account-locked and CRM permission-request smoke cases produced valid `LLM_SHADOW` candidate decisions, while the user-facing response still came from the deterministic baseline.

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

The next phase keeps the deterministic baseline and makes shadow evaluation more useful:

1. Add focused LLM Eval cases for accepted decisions, parser failures, unauthorized tools, unsafe pending actions, and fallback traces.
2. Generate a one-command acceptance report for build/test status, secret scan, shadow Eval metrics, and known limits.
3. Expand trace details with explicit fallback reasons where needed.
4. Promote to `llm` or `hybrid` mode only after shadow Eval cases show stable behavior.

DeepSeek keys must be supplied through environment variables, never committed.
