# Scenario Demo Reproducibility Notes

These notes define the local reproducibility contract for `scripts/demo-scenarios.ps1`.

## Goal

The scenario demo must remain stable when it is run repeatedly against the same local app instance. Old tickets from a previous run must not be mistaken for the current run's evidence.

## Binding Rule

The script binds follow-up evidence to the ticketId returned by `POST /api/agent/chat` first.

For debugging and backward compatibility, each submitted ticket also includes a `scenarioRunId` marker in the title and description. If a future older server response does not include `ticketId`, the script can use `scenarioRunId` as a fallback marker to find the ticket created by the current run.

## Local Repeat Check

Run the script twice against one running app:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/demo-scenarios.ps1 -BaseUrl http://localhost:8080
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/demo-scenarios.ps1 -BaseUrl http://localhost:8080
```

Expected result:

- first run: `5 passed / 0 failed`
- second run: `5 passed / 0 failed`
- second run has a different `runId`
- second run has newly created `ticketIds`
- reports are written under `target/scenario-acceptance`

## Boundaries

- No real LDAP / SSO / IAM / OA / ITSM integration.
- No real unlock, password reset, permission grant, dispatch, or close-ticket operation.
- Pending actions remain audit-only and use `NOT_EXECUTED_MOCK_ONLY`.
- No LLM main / hybrid routing.
- No pgvector / production RAG.
