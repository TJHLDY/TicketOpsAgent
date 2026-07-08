# Backend API Productization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend read APIs, pending action review APIs, and eval report APIs so the existing Agent chain can be queried, audited, reviewed, and demonstrated without adding frontend, real enterprise integrations, or LLM main mode.

**Architecture:** Keep `/api/agent/chat` as the write-side entry point. Add read-side controllers and small services over the existing JDBC tables. Store the final deterministic category, priority, and risk level on `ticket`, keep pending action review as local audit state only, and expose eval reports from `target/agent-eval`.

**Tech Stack:** Spring Boot 3.5, Spring Web MVC, Spring JDBC, H2/PostgreSQL-compatible SQL, JUnit 5, AssertJ.

---

## Scope

Implement:

- `GET /api/tickets/{ticketId}`
- `GET /api/tickets?status=&category=&requesterId=&page=&size=`
- `GET /api/tickets/{ticketId}/trace`
- `GET /api/tickets/{ticketId}/tool-calls`
- `GET /api/tickets/{ticketId}/pending-actions`
- `GET /api/pending-actions/{actionId}`
- `POST /api/pending-actions/{actionId}/approve`
- `POST /api/pending-actions/{actionId}/reject`
- `GET /api/eval/reports/latest`

Do not implement frontend, login/JWT/RBAC, real unlock/grant execution, pgvector, LLM main mode, hybrid mode, external ticket systems, LDAP, SSO, OA, IAM, or approval workflow engines.

## File Structure

- Modify `src/main/resources/schema.sql`: add ticket decision summary columns and pending action review columns.
- Modify `src/main/java/com/tzq/ticketops/ticket/Ticket.java`: carry category, priority, risk level, and updated time.
- Modify `src/main/java/com/tzq/ticketops/ticket/TicketService.java`: update decision summary and filtered ticket listing.
- Modify `src/main/java/com/tzq/ticketops/web/AgentController.java`: save the deterministic response summary onto the ticket after orchestration.
- Modify `src/main/java/com/tzq/ticketops/agent/AgentExecutionLogRepository.java`: expose trace, tool call, pending action, and review operations.
- Modify `src/main/java/com/tzq/ticketops/agent/JdbcAgentExecutionLogRepository.java`: implement read and review SQL.
- Create `src/main/java/com/tzq/ticketops/web/TicketQueryController.java`: ticket detail/list and ticket-scoped trace/tool/pending action endpoints.
- Create `src/main/java/com/tzq/ticketops/web/PendingActionReviewController.java`: pending action detail, approve, and reject endpoints.
- Create `src/main/java/com/tzq/ticketops/web/EvalReportController.java`: latest eval report endpoint.
- Create small DTO records under `src/main/java/com/tzq/ticketops/web/` only where needed for stable JSON.
- Add controller/repository tests under `src/test/java/com/tzq/ticketops/web/` and `src/test/java/com/tzq/ticketops/agent/`.

## Task 1: Ticket decision summary and query API

- [ ] Write failing tests for ticket detail, missing ticket 404, and filtered ticket list.
- [ ] Run targeted tests and verify they fail because `/api/tickets` does not exist and `ticket` rows do not carry category/priority/risk.
- [ ] Add ticket decision columns and update `TicketService`.
- [ ] Add `TicketQueryController` ticket detail/list endpoints.
- [ ] Run targeted tests and verify they pass.

## Task 2: Trace and tool call read APIs

- [ ] Write failing tests for ordered trace and tool call query responses.
- [ ] Run targeted tests and verify they fail because endpoints do not exist.
- [ ] Add repository read methods including row ids and timestamps.
- [ ] Add `/api/tickets/{ticketId}/trace` and `/api/tickets/{ticketId}/tool-calls`.
- [ ] Run targeted tests and verify they pass.

## Task 3: Pending action query and review APIs

- [ ] Write failing tests for pending action list/detail, approve, reject, missing action 404, repeat review 409, and mock-only execution status.
- [ ] Run targeted tests and verify they fail because review endpoints do not exist.
- [ ] Add review columns to `pending_action`.
- [ ] Add review request/response DTOs and repository status transitions.
- [ ] Add `/api/pending-actions/{actionId}/approve` and `/api/pending-actions/{actionId}/reject`.
- [ ] Run targeted tests and verify they pass.

## Task 4: Eval report API

- [ ] Write failing tests for latest eval report JSON from `target/agent-eval`.
- [ ] Run targeted tests and verify they fail because endpoint does not exist.
- [ ] Add a small service/controller that reads `acceptance-report.md` and `llm-shadow-eval.json` when present.
- [ ] Return a clear empty state when files are absent.
- [ ] Run targeted tests and verify they pass.

## Task 5: Full acceptance, docs, and Obsidian sync

- [ ] Run `mvn test`.
- [ ] Run `scripts\accept.ps1`.
- [ ] Run secret scans.
- [ ] Update README and repo docs with the new backend API productization endpoints.
- [ ] Update Obsidian project control, task queue, daily note, and resume/interview expression.
- [ ] Commit and push the branch.
