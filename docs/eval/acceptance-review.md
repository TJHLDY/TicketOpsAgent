# TicketOpsAgent Acceptance Review

This file is the repo-side index for reviewing the current DeepSeek shadow acceptance stage.

For the phase-close decision, read [DeepSeek Shadow Stage Summary](deepseek-shadow-stage-summary.md).

## One-command gate

Run from the repository root:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

The script:

- runs `mvn test`
- scans committed files for key-shaped secrets
- reads `target/agent-eval/llm-shadow-eval.json`
- records optional live DeepSeek status as `DISABLED` unless explicitly requested
- writes `target/agent-eval/acceptance-report.md`

Optional live smoke:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1 -IncludeLiveDeepSeek
```

With no `DEEPSEEK_API_KEY`, live smoke is reported as `SKIPPED`. With a key, the script starts the application with the `deepseek` profile, sends three smoke cases, records provider/model/prompt/schema/latency/fallback evidence, and stops the local app process. `userRiskLevel` is the deterministic baseline and `shadowRiskLevel` is the LLM shadow candidate. The API key and authorization headers are not written to the report.

## Expected gates

The acceptance report should show:

- `mvn test: PASS`
- `Secret scan: PASS`
- `Shadow eval report: PASS`
- `Live DeepSeek: DISABLED`, `SKIPPED`, `PASS`, or `WARN`

## Shadow eval evidence

The mock LLM shadow eval currently covers:

- accepted account and permission decisions
- safety rejects
- parse failures
- empty response
- unauthorized tool
- missing required tool arguments
- invalid or unauthorized pending action
- pending action and category mismatch
- write action without approval
- invalid confidence, app code, category, and risk level

The key invariant is:

- `userVisibleChangedCount: 0`

This means shadow output is evaluated and logged without changing the deterministic user-facing response.

Shadow trace details should carry audit fields for review:

- `llm_status`
- `fallback_reason`
- `fallback_to`
- `provider`
- `model`
- `prompt_version`
- `schema_version`
- `latency_ms`
- `validation_errors`
- `final_decision_source`
- `user_visible_changed`

The acceptance report also summarizes `traceAuditPassCount` so each mock shadow case proves these audit fields are present.

## Review packet

For ChatGPT or external review, use the Obsidian packet:

```text
C:\Users\tzq\Desktop\codex_memory\Codex Memory\Projects\TicketOpsAgent 智能工单分诊与处理平台\22_ChatGPT评审投喂包_acceptance_report_2026-07-08.md
```

The review should decide whether the current mock shadow eval plus optional live smoke report is enough to claim the shadow stage is evaluable, rollback-safe, and reproducible. The recommended next review target is gap Eval coverage, not LLM main mode.

## Boundaries

- No real enterprise system integration.
- No LDAP, SSO, OA, IAM, or approval workflow integration.
- No automatic unlock, password reset, permission grant, dispatch, or ticket close.
- No LLM-driven user-facing response.
- SOP retrieval is still keyword/table driven, not vector RAG.
