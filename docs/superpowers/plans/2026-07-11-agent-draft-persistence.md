# Agent Draft Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist internal suggestions and user reply drafts as typed ticket messages and expose a read-only audit API.

**Architecture:** Extend `AgentExecutionLog` with two draft fields, persist them transactionally beside existing evidence in `ticket_message`, and expose ordered message records through `TicketQueryController`. No sending behavior is introduced.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring JDBC, H2/PostgreSQL-compatible SQL, JUnit 5, MockMvc.

---

### Task 1: Define the draft persistence contract

**Files:**
- Modify: `src/test/java/com/tzq/ticketops/agent/AgentExecutionLogRepositoryTest.java`
- Modify: `src/main/java/com/tzq/ticketops/agent/AgentExecutionLog.java`
- Create: `src/main/java/com/tzq/ticketops/agent/TicketMessageRecord.java`
- Modify: `src/main/java/com/tzq/ticketops/agent/AgentExecutionLogRepository.java`

- [ ] Write a failing repository test that saves suggestion/reply text and expects two ordered message records.
- [ ] Run `mvn test "-Dtest=AgentExecutionLogRepositoryTest"` and confirm compilation/assertion failure for the missing contract.
- [ ] Add the two fields, message record, and repository query method.

### Task 2: Persist drafts transactionally

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/com/tzq/ticketops/agent/JdbcAgentExecutionLogRepository.java`
- Modify: `src/main/java/com/tzq/ticketops/web/AgentController.java`

- [ ] Add `message_order` with an idempotent migration.
- [ ] Make evidence replacement transactional and include ticket-message deletion/insertion.
- [ ] Pass `AgentResponse.suggestion` and `replyDraft` into `AgentExecutionLog`.
- [ ] Re-run the repository and controller persistence tests until green.

### Task 3: Add the read-only message API

**Files:**
- Modify: `src/test/java/com/tzq/ticketops/web/BackendApiProductizationTest.java`
- Modify: `src/main/java/com/tzq/ticketops/web/TicketQueryController.java`

- [ ] Write a failing MockMvc assertion for `/api/tickets/{ticketId}/messages`, exact types/order/content, and missing-ticket 404.
- [ ] Add `TicketMessageDto` and the read endpoint.
- [ ] Re-run the API suite until green.

### Task 4: Pin acceptance and documentation

**Files:**
- Modify: `src/test/java/com/tzq/ticketops/eval/ScenarioAcceptanceTest.java`
- Create: `docs/api/agent-draft-persistence.md`
- Create: `src/test/java/com/tzq/ticketops/eval/AgentDraftPersistenceArtifactsTest.java`
- Modify: `README.md`

- [ ] Add scenario assertions proving normal/rejected drafts are persisted and no send operation exists.
- [ ] Add a failing artifact test, then document schema, API, transactional replacement, and draft-only boundary.
- [ ] Update current evidence and roadmap after the verified test count is known.

### Task 5: Verify And Land

- [ ] Run targeted tests, full `mvn test`, package, `scripts\accept.ps1`, and five live scenarios.
- [ ] Query the live messages endpoint and prove the response draft equals stored content.
- [ ] Run strict secret/bidi scan and `git diff --check`.
- [ ] Run independent review, fix findings through tests, create a PR, merge, and rerun main acceptance.
- [ ] Update project, module, knowledge, sync, and Daily Obsidian notes without secrets.
