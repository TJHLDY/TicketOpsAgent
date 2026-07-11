# Agent Draft Persistence Design

## Goal

Persist every generated internal handling suggestion and user reply draft as auditable ticket messages, expose them through a read-only ticket API, and keep them strictly draft-only.

## Gap

The Agent response currently contains `suggestion` and `replyDraft`, but `AgentExecutionLog` persists only trace events, tool calls, and pending actions. The existing `ticket_message` table is unused. A process restart therefore loses the generated drafts even though the initial MVP requires drafts to be saved and never auto-sent.

## Considered Approaches

### Put draft content in `agent_trace.detail`

Rejected. Trace strings are operational events, not a typed message contract. Mixing full drafts into trace details makes querying brittle and confuses audit metadata with user-facing content.

### Add suggestion/reply columns to `ticket`

Rejected. A ticket is the request aggregate; draft messages can evolve independently and the schema already contains a dedicated `ticket_message` table.

### Use `ticket_message` as typed Agent output storage

Selected. `AgentExecutionLog` carries the generated suggestion and reply draft into the persistence boundary. The repository writes exactly two ordered message rows with fixed `sender_type` values: `INTERNAL_SUGGESTION` and `USER_REPLY_DRAFT`. A read-only `/api/tickets/{ticketId}/messages` endpoint returns typed records.

## Data And API Contract

`ticket_message` gains `message_order` for deterministic ordering while retaining `sender_type` for compatibility. New records expose `id`, `ticketId`, `messageOrder`, `senderType`, `content`, and `createdAt`.

Every completed Agent request persists:

1. `INTERNAL_SUGGESTION` containing `AgentResponse.suggestion`.
2. `USER_REPLY_DRAFT` containing `AgentResponse.replyDraft`.

The API is read-only. No send endpoint, delivery status, recipient integration, or external notification is added. A stored reply remains a draft even after a pending action is approved or rejected.

## Atomicity

`JdbcAgentExecutionLogRepository.save` writes trace, tool calls, pending actions, and messages in one Spring transaction. Re-saving evidence for the same ticket replaces all four evidence groups consistently.

## Verification

- Repository test proves both messages survive save/load with stable order and types.
- Controller persistence test proves the generated response and stored drafts match exactly.
- Backend API test proves the read endpoint shape and 404 behavior.
- Scenario acceptance proves both normal and rejected tickets retain drafts while no automatic send/write side effect occurs.
- Existing 119 tests remain green; full acceptance, package, live scenarios, secret scan, and independent review close the PR.

## Boundary

This does not add email/chat delivery, templates, editing, version history, user notification, real ITSM integration, or real write execution.
