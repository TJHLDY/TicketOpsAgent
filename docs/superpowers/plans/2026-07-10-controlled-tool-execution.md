# Controlled Read-Only Tool Execution Plan

**Goal:** Close the tool trust boundary by executing `AgentDecision.toolIntents()` only through a backend-owned, budgeted, typed, auditable read-only executor.

### Task 1: Add failing execution-boundary tests

- [x] Valid account status intent executes once and records normalized arguments.
- [x] Valid permission intent normalizes `appCode` and returns a stable summary.
- [x] Unknown tool, category mismatch, requester mismatch, missing argument, extra argument, and unsupported app code are rejected before invocation.
- [x] Multiple intents and configured budget overflow are rejected before invocation.
- [x] Orchestrator rejection returns `TOOL_REJECT`, zero tool calls, and zero pending actions.

### Task 2: Implement typed DTOs and executor

- [x] Add typed account-status and permission-query argument records.
- [x] Add stable rejection reason codes and exception type.
- [x] Add `ReadOnlyToolExecutor` with allowlist, category policy, exact schemas, requester binding, app-code normalization, and call budget.
- [x] Keep tool implementations mock-only and read-only.

### Task 3: Route the orchestrator through the boundary

- [x] Pass primary decision tool intents into category handlers.
- [x] Replace direct mock-tool calls with executor results.
- [x] Add `TOOL_DECISION`, enriched `TOOL_CALL`, and `TOOL_REJECT` traces.
- [x] Stop before draft-dependent pending actions when execution is rejected.

### Task 4: Strengthen parser consistency and documentation

- [x] Keep parser early validation and executor authority aligned for allowlist, one-intent budget, exact schemas, and requester binding.
- [x] Document the trust boundary, budget, validation, traces, and mock-only limits.
- [x] Add artifact/readiness contracts and update the verified test count only after the full suite passes.

### Task 5: Verify and publish

- [x] Run targeted tests RED then GREEN.
- [x] Run full Maven tests and package.
- [x] Run `scripts\accept.ps1`.
- [x] Run live five-scenario smoke.
- [x] Run strict secret scan and `git diff --check`.
- [ ] Run independent review, create PR, merge, and re-run acceptance on `main`.
- [ ] Update Obsidian project, Daily, sync, and reusable tool-safety knowledge notes.
