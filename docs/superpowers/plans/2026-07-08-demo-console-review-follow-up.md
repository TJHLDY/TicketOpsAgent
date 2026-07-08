# Demo Console Review Follow-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the minimal PR #4 review follow-up: strengthen audit-only copy, add PR boundary checklist, and collect merge-ready hygiene evidence.

**Architecture:** Keep the existing Spring Boot static page and vanilla JavaScript implementation. Add only boundary copy and artifact assertions; use repository and PR metadata checks for non-code evidence.

**Tech Stack:** Spring Boot static resources, JUnit artifact tests, Maven, PowerShell hygiene scans, GitHub CLI.

---

## File Structure

- Modify `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`
  - Assert the Pending Actions area states that review never unlocks accounts, resets passwords, grants permissions, dispatches tickets, or closes tickets.
- Modify `src/main/resources/static/demo-console.html`
  - Add the explicit audit-only red-line sentence in the Pending Actions notice.
- Update PR #4 body through GitHub CLI
  - Add a boundary checklist covering static page scope, vanilla JS, existing APIs only, no real integrations, no real execution, DeepSeek shadow-only, deterministic user-facing flow, and `NOT_EXECUTED_MOCK_ONLY`.

## Task 1: Pending Action Boundary Copy

- [ ] **Step 1: Write failing artifact assertion**

Add this assertion to `DemoConsoleArtifactsTest`:

```java
.contains("Approve Review / Reject Review never unlock accounts, reset passwords, grant permissions, dispatch tickets, or close tickets.")
```

- [ ] **Step 2: Run targeted test and verify RED**

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: FAIL because the new sentence is not yet in `demo-console.html`.

- [ ] **Step 3: Add minimal HTML copy**

Add the sentence to the existing Pending Actions `<p class="notice">`:

```html
Approve Review / Reject Review never unlock accounts, reset passwords, grant permissions, dispatch tickets, or close tickets.
```

- [ ] **Step 4: Run targeted test and verify GREEN**

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: PASS.

## Task 2: PR Boundary Checklist

- [ ] **Step 1: Update PR #4 body**

Append this checklist:

```markdown
## Boundary Checklist

- [x] Spring Boot static page at `/demo-console.html`
- [x] Vanilla JS only
- [x] No React / Vite / Next.js
- [x] Calls existing backend APIs only
- [x] No new backend business capability
- [x] No login / JWT / RBAC
- [x] No real enterprise integration
- [x] No real unlock / reset password / grant permission / dispatch / close
- [x] DeepSeek remains shadow candidate only
- [x] Deterministic result remains user-facing main flow
- [x] Pending action review keeps `NOT_EXECUTED_MOCK_ONLY`
```

- [ ] **Step 2: Verify PR body**

```powershell
gh pr view 4 --json body
```

Expected: body includes `## Boundary Checklist`.

## Task 3: Merge-Ready Evidence

- [ ] **Step 1: Run frontend engineering boundary scan**

```powershell
Test-Path package.json
Test-Path vite.config.*
Test-Path next.config.*
git grep -n "react" .
git grep -n "vite" .
git grep -n "next" .
git grep -n "webpack" .
git grep -n "package-lock" .
```

Expected: no frontend engineering entry points. Natural-language documentation hits are acceptable only if they are not dependency or config files.

- [ ] **Step 2: Run bidi control-character scan**

```powershell
$pattern = "[\u202A-\u202E\u2066-\u2069]"
Get-ChildItem -Recurse -File |
  Where-Object { $_.FullName -notmatch "\\.git\\|\\target\\|\\.playwright-cli\\" } |
  Select-String -Pattern $pattern
```

Expected: no matches.

- [ ] **Step 3: Re-run targeted tests, full tests, acceptance, HTTP smoke, and Playwright smoke**

Expected: all previous PR #4 evidence remains green.

## Self-Review

- Spec coverage: covers both copy changes and all three requested evidence items.
- Placeholder scan: no unfinished placeholder markers.
- Type consistency: uses existing artifact test and existing static page.
