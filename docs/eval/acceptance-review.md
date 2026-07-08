# TicketOpsAgent Acceptance Review

This file is the repo-side index for reviewing the current DeepSeek shadow acceptance stage.

## One-command gate

Run from the repository root:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

The script:

- runs `mvn test`
- scans committed files for key-shaped secrets
- reads `target/agent-eval/llm-shadow-eval.json`
- writes `target/agent-eval/acceptance-report.md`

## Expected gates

The acceptance report should show:

- `mvn test: PASS`
- `Secret scan: PASS`
- `Shadow eval report: PASS`

## Shadow eval evidence

The mock LLM shadow eval currently covers:

- accepted account and permission decisions
- safety rejects
- parse failures
- empty response
- unauthorized tool
- invalid or unauthorized pending action
- write action without approval
- invalid confidence, app code, category, and risk level

The key invariant is:

- `userVisibleChangedCount: 0`

This means shadow output is evaluated and logged without changing the deterministic user-facing response.

## Review packet

For ChatGPT or external review, use the Obsidian packet:

```text
C:\Users\tzq\Desktop\codex_memory\Codex Memory\Projects\TicketOpsAgent 智能工单分诊与处理平台\22_ChatGPT评审投喂包_acceptance_report_2026-07-08.md
```

The review should decide whether the current mock shadow eval plus acceptance report is enough to claim the shadow stage is evaluable, rollback-safe, and reproducible, or whether the next step should add optional live DeepSeek smoke reporting.

## Boundaries

- No real enterprise system integration.
- No LDAP, SSO, OA, IAM, or approval workflow integration.
- No automatic unlock, password reset, permission grant, dispatch, or ticket close.
- No LLM-driven user-facing response.
- SOP retrieval is still keyword/table driven, not vector RAG.
