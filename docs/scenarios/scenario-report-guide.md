# Scenario Report Guide

This guide explains how to run the local scenario demo report for TicketOpsAgent.

The report is a developer acceptance artifact. It proves that the current backend APIs can replay the five agreed MVP scenarios and collect auditable evidence. It is not a production monitoring report.

## Run

Start the application:

```powershell
mvn spring-boot:run
```

Run the scenario demo script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/demo-scenarios.ps1 -BaseUrl http://localhost:8080
```

The script writes:

- `target/scenario-acceptance/scenario-report.json`
- `target/scenario-acceptance/scenario-report.md`

Use `-ShowPlan` to print the planned flow without calling the server:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts/demo-scenarios.ps1 -ShowPlan
```

## Scenario Coverage

The script submits one ticket per scenario through `POST /api/agent/chat`, then reads persisted evidence from ticket, trace, tool-call, and pending-action APIs.

| Scenario id | Expected category | Expected risk | Expected evidence |
| --- | --- | --- | --- |
| `oa-account-locked` | `ACCOUNT_LOCKED` | `NEEDS_APPROVAL` | `SOP-ACCOUNT-LOCKED`, `getAccountStatus`, `LOCKED`, `UNLOCK_ACCOUNT`, `NOT_EXECUTED_MOCK_ONLY` |
| `crm-permission-request` | `PERMISSION_REQUEST` | `NEEDS_APPROVAL` | `SOP-PERMISSION-REQUEST`, `getUserPermissions`, `NONE`, `GRANT_PERMISSION`, `NOT_EXECUTED_MOCK_ONLY` |
| `vpn-mfa-issue` | `MFA_ISSUE` | `NEEDS_APPROVAL` | classification trace, no tool call, no pending action |
| `prompt-injection-rejection` | `PERMISSION_REQUEST` | `REJECT` | rejection trace, no tool call, no pending action |
| `non-it-request-rejection` | `UNKNOWN` | `REJECT` | rejection trace, no tool call, no pending action |

## JSON Shape

The generated JSON report contains these top-level fields:

```json
{
  "generatedAt": "2026-07-09T00:00:00.0000000-04:00",
  "baseUrl": "http://localhost:8080",
  "totalScenarios": 5,
  "passedScenarios": 5,
  "failedScenarios": 0,
  "scenarioResults": [],
  "boundaries": []
}
```

Each item in `scenarioResults` includes the scenario id, ticket id, category, priority, risk level, retrieved document id, called tool, tool result, pending action, execution status, trace steps, result, and error.

## Boundaries

- No real LDAP / SSO / IAM / OA / ITSM integration.
- No real unlock, password reset, permission grant, dispatch, or close-ticket operation.
- Pending actions remain audit-only and use `NOT_EXECUTED_MOCK_ONLY`.
- No LLM main / hybrid routing.
- No pgvector / production RAG.
- No complex frontend or production approval workflow is added by this script.

## How To Review

Treat a passing report as evidence that the current backend demo can be reproduced locally:

1. The five MVP scenarios still route as expected.
2. Tool calls remain read-only.
3. Unsafe or unknown requests do not call tools.
4. Write-like outcomes are stored only as pending actions.
5. The report files are generated under `target/`, so they should not be committed.
