# Agent Draft Persistence

## Purpose

TicketOpsAgent persists the Agent's internal handling suggestion and user-facing reply draft as audit evidence. This closes the gap between values returned by `/api/agent/chat` and evidence that can be inspected after the request completes.

## Storage Contract

Both drafts are stored in `ticket_message` in the same transaction as trace events, tool call logs, and pending actions:

| `sender_type` | Meaning | `message_order` |
| --- | --- | --- |
| `INTERNAL_SUGGESTION` | Internal handling suggestion for an operator | `0` |
| `USER_REPLY_DRAFT` | User-facing reply draft awaiting review | `1` |

Replacing an execution log replaces only the two generated draft sender types and its related execution evidence atomically. Other `ticket_message` sender types are preserved. A failed insert rolls back the transaction instead of leaving a partial audit trail.

`AgentResponse` bounds each generated draft to 4,000 characters before it is returned or persisted. The API response and stored evidence therefore use the same normalized content and cannot exceed the `ticket_message.content` contract.

## Read API

```http
GET /api/tickets/{ticketId}/messages
```

The endpoint returns the two records ordered by `messageOrder`. A missing ticket uses the existing `TICKET_NOT_FOUND` API error contract.

## Safety Boundary

This capability is draft-only. No send endpoint, delivery state, outbound connector, or automatic user notification is implemented. The API is read-only and does not turn a reply draft into an executed action.
