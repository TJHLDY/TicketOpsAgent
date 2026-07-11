# Controlled Read-Only Tool Execution Design

## Context

TicketOpsAgent currently has two read-only mock tools and the DeepSeek shadow parser rejects unknown tool names and missing arguments. The user-facing orchestrator, however, ignores `AgentDecision.toolIntents()` and directly invokes concrete tool classes from category-specific branches. That leaves validation and execution on separate paths and weakens the claim that tool intents are controlled by the backend.

## Goal

Route every user-facing read-only tool call through one backend-owned execution boundary that validates tool identity, category compatibility, exact arguments, requester identity, application code, and per-request call budget before invoking a tool. Rejections must be auditable and must not create a pending action.

## Non-goals

- No LLM main or hybrid mode.
- No autonomous tool loop.
- No write tool implementation.
- No real LDAP, SSO, IAM, OA, or ITSM connection.
- No MCP gateway or generic plugin system.
- No change to the audit-only pending-action execution boundary.

## Trust boundary

`AgentDecision.toolIntents()` is untrusted proposal data even when produced by deterministic rules. The backend executor owns:

- the allowlist: `getAccountStatus`, `getUserPermissions`;
- category-to-tool compatibility;
- exact argument names;
- conversion to typed argument DTOs;
- binding `userId` to the request requester;
- supported `appCode` values;
- maximum calls per request;
- actual invocation and result summarization.

No model- or rule-provided tool name or argument reaches a tool implementation before these checks pass.

## Execution flow

```text
AgentDecision
-> RAG accepted
-> ReadOnlyToolExecutor.executeSingle(...)
-> budget / allowlist / category / exact args / requester binding
-> typed DTO
-> mock read-only tool
-> ToolCallRecord + TOOL_CALL trace
```

Any validation or invocation failure returns a `TOOL_REJECT` trace, no successful `ToolCallRecord`, no pending action, and a human-review response.

## Observability

Successful calls record:

- requested tool;
- validated tool;
- normalized arguments;
- budget used and limit;
- result summary.

Rejected calls record only a stable reason code, requested tool name, and budget limit. Raw exception messages are not exposed to the user.

## Verification

- valid account and permission intents execute exactly once;
- lower-case app code is normalized;
- unknown tools, category mismatch, requester mismatch, missing/extra arguments, unsupported app codes, duplicate intents, and budget overflow fail closed;
- executor rejection causes zero pending actions in the orchestrator;
- existing five scenarios and persistence logs remain compatible;
- full acceptance and live scenario report pass.

