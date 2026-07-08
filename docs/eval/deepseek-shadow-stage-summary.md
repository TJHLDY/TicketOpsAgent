# DeepSeek Shadow Stage Summary

This document closes the current DeepSeek shadow evaluation stage for the TicketOpsAgent spike.

## Stage Objective

The objective was not to let an LLM control ticket handling. The objective was to prove that a Spring AI-backed model can be introduced as a shadow candidate while the deterministic Java chain remains the user-facing baseline.

The stage is considered closed when the shadow path is:

- evaluable: mock cases and optional live smoke produce reviewable reports.
- rollback-safe: invalid model output falls back to deterministic behavior.
- reproducible: a one-command acceptance script recreates the evidence without requiring a live API key.

## Current Evidence

Latest verified evidence for this stage:

- `mvn test`: PASS, 46 tests.
- `Secret scan`: PASS.
- `Shadow eval report`: PASS.
- Optional live DeepSeek smoke: PASS when run with a local `DEEPSEEK_API_KEY`.
- Mock shadow eval cases: 34.
- `parseSuccessCount`: 31.
- `validationSuccessCount`: 12.
- `fallbackCount`: 22.
- `safetyPassCount`: 9 of 9.
- `traceAuditPassCount`: 34 of 34.
- `userVisibleChangedCount`: 0.

The most important invariant is `userVisibleChangedCount: 0`. It proves the LLM candidate is evaluated and audited without changing the response returned by the deterministic chain.

## Acceptance Command

Run the default acceptance gate from the repository root:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

The default gate runs Maven tests, scans committed files for key-shaped secrets, reads the mock shadow eval JSON, and writes:

```text
target/agent-eval/acceptance-report.md
```

Optional live DeepSeek smoke:

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1 -IncludeLiveDeepSeek
```

Without `DEEPSEEK_API_KEY`, the live smoke section is `SKIPPED`. With a key, the script starts the app on a temporary port, runs three smoke cases, records provider/model/prompt/schema/latency/fallback evidence, and stops the process. The key is not written to the report.

## Mock Eval vs Live Smoke

Mock shadow eval is the main reproducible acceptance signal. It does not use the network or a real API key, so it is stable in CI and local review.

Live smoke is an integration sanity check. It proves the Spring AI DeepSeek profile can reach the real shadow path, but it is not the primary measure of model quality.

## Why 12 Validations Out Of 34 Is Acceptable

`validationSuccessCount: 12` is not a quality score for the model. The eval set intentionally includes malformed JSON, unauthorized tools, invalid pending actions, missing tool arguments, category/action mismatches, and safety cases.

The expected behavior for those cases is fallback, not validation success. That is why `fallbackCount: 22`, `safetyPassCount: 9 of 9`, and `userVisibleChangedCount: 0` matter more for this stage.

## Safety Boundary

The deterministic route remains the only user-facing route. DeepSeek generates a candidate `AgentDecision` only.

The Java parser and validator enforce:

- allowed categories, priorities, and risk levels.
- confidence must be between `0.0` and `1.0`.
- tools must be in the read-only allowlist: `getAccountStatus`, `getUserPermissions`.
- `getAccountStatus` requires `userId`.
- `getUserPermissions` requires `userId` and `appCode`.
- `appCode` must be one of `OA`, `CRM`, `ERP`, `VPN`.
- pending actions are limited to `UNLOCK_ACCOUNT` and `GRANT_PERMISSION`.
- pending actions require approval.
- `READ_ONLY` and `REJECT` decisions cannot carry pending actions.
- pending action type must match the ticket category.

Any parse, validation, or API failure records `LLM_SHADOW_FAILED` and falls back to deterministic behavior.

## Trace Audit

Shadow traces include review fields such as:

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

`traceAuditPassCount: 34 of 34` confirms every mock shadow eval case carried the expected audit fields.

## What This Stage Does Not Claim

Do not claim:

- production LLM decision quality.
- LLM main mode.
- hybrid mode.
- automatic tool calling controlled by the model.
- real RAG or pgvector.
- real enterprise ticket system integration.
- LDAP, SSO, OA, IAM, or approval workflow integration.
- automatic unlock, password reset, permission grant, dispatch, or ticket close.
- large-scale model accuracy.

## Close Decision

The DeepSeek shadow stage is closed for the current spike. Further work should move to documentation, resume/interview material, merge/tag hygiene, or a separately scoped next phase.

Promotion to `hybrid` or `llm` mode requires a new plan with stricter acceptance criteria, not an incremental change inside this stage.
