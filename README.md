# TicketOpsAgent

TicketOpsAgent is a Spring Boot + Spring AI-ready backend spike for enterprise IT account and permission tickets.

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
- Spring AI 1.1.8 BOM, with ChatClient integration prepared for the next phase.
- PostgreSQL Docker Compose profile for local persistence.
- H2 default profile for fast tests.
- 32 automated tests passing at the latest verification.
- `AgentDecisionPort` boundary in place with deterministic and shadow-mode scaffolding.
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
- `ticketops.agent.mode=shadow` keeps deterministic user-facing output and records shadow decision traces. If no LLM decision port is configured yet, it records `LLM_SHADOW_SKIPPED`.
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

Current shadow behavior is intentionally conservative: deterministic output is still returned to the caller. The next implementation step is a DeepSeek-backed `AgentDecisionPort` that writes comparable LLM decisions into trace logs.

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

The next phase keeps the deterministic baseline and adds real model support safely:

1. Add a DeepSeek-backed `AgentDecisionPort` through Spring AI `ChatClient`.
2. Keep `shadow` mode first: return deterministic results while recording LLM decisions for comparison.
3. Add fallback fields such as `llm_status`, `fallback_reason`, and `fallback_to` to trace logs.
4. Promote to `llm` or `hybrid` mode only after Eval cases show stable behavior.

DeepSeek keys must be supplied through environment variables, never committed.
