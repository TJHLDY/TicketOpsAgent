# API Error Contract And Pending Action State Machine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden TicketOpsAgent backend API failures into a stable JSON error contract and make pending action review terminal states explicit.

**Architecture:** Add a small web error layer with typed error codes, one domain exception, and one global handler. Keep controller behavior deterministic and audit-only; pending action review may update local review state but must never execute real account operations.

**Tech Stack:** Spring Boot 3, Jakarta Validation, MockMvc, H2-backed integration tests, static Demo Console JavaScript.

---

### Task 1: API Error Contract Tests

**Files:**
- Create: `src/test/java/com/tzq/ticketops/web/ApiErrorContractTest.java`
- Modify after red: `src/main/java/com/tzq/ticketops/web/*`

- [ ] **Step 1: Write failing tests**

Cover:
- `GET /api/tickets/missing-ticket` returns `404` with `errorCode=TICKET_NOT_FOUND`.
- `GET /api/pending-actions/999999` returns `404` with `errorCode=PENDING_ACTION_NOT_FOUND`.
- `POST /api/pending-actions/999999/approve` returns `404` with `errorCode=PENDING_ACTION_NOT_FOUND`.
- invalid review request returns `400` with `errorCode=INVALID_REQUEST`.

- [ ] **Step 2: Run tests to verify red**

Run:

```powershell
mvn test "-Dtest=ApiErrorContractTest"
```

Expected: tests fail because Spring currently returns the default error body or no stable `errorCode`.

- [ ] **Step 3: Implement minimal web error layer**

Create:
- `ApiErrorCode`
- `ApiException`
- `ApiErrorResponse`
- `GlobalExceptionHandler`

Replace controller `ResponseStatusException` use with `ApiException`.

- [ ] **Step 4: Run tests to verify green**

Run:

```powershell
mvn test "-Dtest=ApiErrorContractTest"
```

Expected: pass.

### Task 2: Pending Action Review State Machine Tests

**Files:**
- Create: `src/test/java/com/tzq/ticketops/web/PendingActionReviewStateMachineTest.java`
- Modify after red: `src/main/java/com/tzq/ticketops/web/PendingActionReviewController.java`

- [ ] **Step 1: Write failing tests**

Cover:
- approving an already approved action returns `409` with `PENDING_ACTION_ALREADY_REVIEWED`.
- rejecting an already approved action returns `409` with `PENDING_ACTION_ALREADY_REVIEWED`.
- approving a cancelled action returns `409` with `PENDING_ACTION_ALREADY_REVIEWED`.
- blank `reviewerId` returns `400` with `INVALID_REQUEST`.
- overlong `reviewComment` returns `400` with `INVALID_REQUEST`.
- successful approval keeps `executionStatus=NOT_EXECUTED_MOCK_ONLY`.

- [ ] **Step 2: Run tests to verify red**

Run:

```powershell
mvn test "-Dtest=PendingActionReviewStateMachineTest"
```

Expected: tests fail on missing error contract and overlong comment validation.

- [ ] **Step 3: Implement minimal validation/state checks**

Add a `@Size(max = 200)` review comment limit and throw `ApiException.conflict(PENDING_ACTION_ALREADY_REVIEWED)` for all non-`PENDING` statuses.

- [ ] **Step 4: Run tests to verify green**

Run:

```powershell
mvn test "-Dtest=PendingActionReviewStateMachineTest"
```

Expected: pass.

### Task 3: Demo Console Error Display

**Files:**
- Modify: `src/main/resources/static/demo-console.js`
- Modify test: `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`

- [ ] **Step 1: Write failing artifact assertions**

Assert the Demo Console JavaScript formats API errors with `status`, `errorCode`, and `message`, and no longer uses `alert(` for review errors.

- [ ] **Step 2: Run tests to verify red**

Run:

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: fail on missing formatter and existing alert.

- [ ] **Step 3: Implement minimal JavaScript change**

Parse JSON error bodies in `requestJson`, format them in `renderError`, and render review errors inside the existing result panel instead of an alert.

- [ ] **Step 4: Run tests to verify green**

Run:

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: pass.

### Task 4: API Docs And Acceptance

**Files:**
- Modify: `docs/api/backend-api-productization.md`
- Optionally update: `README.md` only if acceptance numbers change.

- [ ] **Step 1: Add API Error Contract docs**

Document the response shape and common cases table.

- [ ] **Step 2: Run focused and full verification**

Run:

```powershell
mvn test "-Dtest=ApiErrorContractTest,PendingActionReviewStateMachineTest,DemoConsoleArtifactsTest"
mvn test
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

Expected: all pass and acceptance keeps shadow eval, safety, trace audit, and user-visible gates stable.
