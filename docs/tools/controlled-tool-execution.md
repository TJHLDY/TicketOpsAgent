# Controlled Read-Only Tool Execution

## Purpose

TicketOpsAgent treats every `AgentDecision.toolIntents` entry as an untrusted proposal. A deterministic rule or an LLM candidate may propose a tool, but only the backend-owned `ReadOnlyToolExecutor` can validate and invoke it.

## Allowlist and schemas

Only two mock-only read operations are registered:

| Tool | Category | Exact argument schema | Result |
|---|---|---|---|
| `getAccountStatus` | `ACCOUNT_LOCKED` | `userId` | `LOCKED`, `ACTIVE`, `MFA_REQUIRED`, or `UNKNOWN` |
| `getUserPermissions` | `PERMISSION_REQUEST` | `userId`, `appCode` | comma-separated permission codes or `NONE` |

The executor requires the exact argument names. Missing arguments and extra arguments are rejected. Input maps are converted to typed `GetAccountStatusArgs` or `GetUserPermissionsArgs` only after validation.

## Identity and scope validation

- Proposed `userId` must equal the authenticated request-level requester identity carried by the ticket request.
- Supported application codes are `OA`, `CRM`, `ERP`, and `VPN`.
- Application codes are trimmed and normalized to uppercase before invocation and audit logging.
- A category may call only its registered tool. An account-lock decision cannot query arbitrary permissions, and a permission decision cannot query account status.

This is a mock prototype and does not provide authentication. The requester check is an internal binding invariant, not a claim of production IAM authorization.

## Call budget

The default configuration is:

```yaml
ticketops:
  tools:
    max-calls-per-request: 1
```

`ReadOnlyToolExecutor.executeSingle` rejects empty intent lists, duplicate intents, and any request that exceeds `ticketops.tools.max-calls-per-request`. The current implementation accepts only the configured value `1` and fails application startup for any other value. There is no autonomous or multi-tool loop.

## Traces and failure behavior

Before execution, the Agent records `TOOL_DECISION` with the requested tool, intent count, and budget limit. A successful call records `TOOL_CALL` with validation status, budget usage, and result summary. Normalized arguments are persisted in `tool_call_log`.

Rejected execution records `TOOL_REJECT` with a stable reason such as:

- `UNAUTHORIZED_TOOL`
- `CATEGORY_TOOL_MISMATCH`
- `INVALID_ARGUMENTS`
- `REQUESTER_MISMATCH`
- `UNSUPPORTED_APP_CODE`
- `TOOL_BUDGET_EXCEEDED`
- `TOOL_INVOCATION_FAILED`

A rejection produces zero successful tool calls and zero pending actions. The response transfers the ticket to human support without exposing raw exceptions.

Successful invocation does not automatically authorize a later action. The account flow proposes `UNLOCK_ACCOUNT` only when the validated read result is `LOCKED`; other account states produce no unlock pending action.

Business decisions use the structured `emptyResult` field, not the display summary. A real permission code whose text is `NONE` is therefore treated as existing data rather than confused with an empty permission list.

## Boundary

- No write tool exists.
- No real account unlock, password reset, permission grant, ticket dispatch, or close operation is executed.
- The tools read only seeded mock database tables.
- Pending actions remain audit-only with `NOT_EXECUTED_MOCK_ONLY`.
- DeepSeek remains shadow-only and cannot invoke the executor on the user-facing path.
- This is not an MCP gateway or a generic plugin runtime.

## Verification

`ReadOnlyToolExecutorTest` covers accepted account/permission calls, normalization, allowlist rejection, category mismatch, requester mismatch, exact schemas, supported app codes, null input, missing intents, and budget overflow. `AgentOrchestratorTest` proves that a malicious primary intent is stopped after RAG and before tool or pending-action side effects, that an `ACTIVE` result does not produce an unlock proposal, and that a permission code named `NONE` is not mistaken for an empty result.
