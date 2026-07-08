# Backend API Productization Guide

This guide shows how to demonstrate the backend API productization stage after the app is running locally.

The goal is to prove that TicketOpsAgent is no longer only an `/api/agent/chat` spike. The backend can now create a ticket, expose the persisted decision summary, show audit traces, show tool calls, show pending actions, review a pending action, and expose the latest eval report.

## Scope

This guide covers the local demo flow only:

1. `POST /api/agent/chat`
2. `GET /api/tickets/{ticketId}`
3. `GET /api/tickets/{ticketId}/trace`
4. `GET /api/tickets/{ticketId}/tool-calls`
5. `GET /api/tickets/{ticketId}/pending-actions`
6. `POST /api/pending-actions/{actionId}/approve`
7. `GET /api/eval/reports/latest`

The eval report endpoint is for local demo and development review only. It is not a production monitoring API.

## Prerequisites

Generate the local acceptance report first:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

Start the app with the default H2 profile:

```powershell
mvn spring-boot:run
```

The demo assumes the app is listening at `http://localhost:8080`.

By default, the script uses seeded mock user `mock-user-001` so the read-only account status tool returns `LOCKED`.

## Run The Demo Script

In another PowerShell terminal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1
```

To print the demo plan without calling the server:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1 -ShowPlan
```

To target another local port:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1 -BaseUrl http://localhost:18080
```

To use another requester id:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\demo-backend-api.ps1 -RequesterId mock-user-001
```

## Expected Evidence

The script creates one account-locked demo ticket, then reads the persisted backend evidence:

- ticket decision summary: `ACCOUNT_LOCKED`, `P2`, `NEEDS_APPROVAL`
- trace timeline: classification, SOP retrieval, tool call, suggestion, pending action
- tool call evidence: `getAccountStatus` returns `LOCKED` for the default seeded user
- pending action evidence: `UNLOCK_ACCOUNT`
- review response: `APPROVED`
- execution boundary: `NOT_EXECUTED_MOCK_ONLY`
- eval report gates: Maven test, secret scan, shadow eval report, live DeepSeek state

No real account operation is executed. Pending action review only updates local audit/review state.

## Manual Curl-Style Flow

Create a ticket:

```powershell
$body = @{
  requesterId = "demo-user-001"
  title = "OA account locked demo"
  description = "OA account locked demo"
} | ConvertTo-Json -Compress

Invoke-RestMethod -Method Post http://localhost:8080/api/agent/chat `
  -ContentType "application/json" `
  -Body $body
```

Find the ticket by requester:

```powershell
Invoke-RestMethod "http://localhost:8080/api/tickets?status=OPEN&category=ACCOUNT_LOCKED&requesterId=demo-user-001&page=0&size=1"
```

Inspect the ticket and audit evidence:

```powershell
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/trace
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/tool-calls
Invoke-RestMethod http://localhost:8080/api/tickets/{ticketId}/pending-actions
```

Approve the pending action for audit demo only:

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/pending-actions/{actionId}/approve `
  -ContentType "application/json" `
  -Body '{"reviewerId":"admin-mock","reviewComment":"Approved for audit demo"}'
```

Read the latest eval report:

```powershell
Invoke-RestMethod http://localhost:8080/api/eval/reports/latest
```

## Boundaries

This demo does not add:

- LLM main mode
- hybrid mode
- pgvector or embedding RAG
- frontend
- LDAP, SSO, OA, IAM, or real ticket system integration
- real account unlock
- real password reset
- real permission grant
- real dispatch or close-ticket operation
- login, JWT, RBAC, or approval workflow engine

## Troubleshooting

If the script cannot connect, start the app first:

```powershell
mvn spring-boot:run
```

If `GET /api/eval/reports/latest` returns unavailable values, regenerate the local acceptance report:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```
