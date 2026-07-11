# Privacy-Safe Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add low-cardinality Micrometer metrics and a local Actuator diagnostics surface without exposing ticket, RAG, tool, or model content.

**Architecture:** Introduce an `AgentTelemetry` application boundary with no-op and Micrometer implementations. Report typed outcomes directly from `AgentOrchestrator`, use Actuator for local inspection, and explicitly disable every Spring AI sensitive-content observation switch.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0, Micrometer, Spring Boot Actuator, JUnit 5, AssertJ, MockMvc.

---

### Task 1: Add the telemetry contract with test-first metric semantics

**Files:**
- Create: `src/test/java/com/tzq/ticketops/observability/MicrometerAgentTelemetryTest.java`
- Create: `src/main/java/com/tzq/ticketops/observability/AgentTelemetry.java`
- Create: `src/main/java/com/tzq/ticketops/observability/AgentRequestOutcome.java`
- Create: `src/main/java/com/tzq/ticketops/observability/MicrometerAgentTelemetry.java`

- [ ] **Step 1: Write failing tests** for a completed request timer, RAG outcome counter, successful and rejected tool counters, pending-action counter, shadow fallback counter, and sensitive-marker absence from all meter IDs.
- [ ] **Step 2: Run** `mvn test "-Dtest=MicrometerAgentTelemetryTest"` and confirm compilation fails because the observability types do not exist.
- [ ] **Step 3: Implement the minimal telemetry boundary** using `Timer`, `Counter`, fixed metric names, and normalization that accepts only enum/fixed values, the two allowlisted tools, and `offline|onnx|other` providers.
- [ ] **Step 4: Re-run** the targeted test and confirm it passes.

### Task 2: Integrate typed pipeline outcomes

**Files:**
- Modify: `src/test/java/com/tzq/ticketops/agent/AgentOrchestratorTest.java`
- Modify: `src/test/java/com/tzq/ticketops/agent/AgentOrchestratorShadowFailureTest.java`
- Modify: `src/main/java/com/tzq/ticketops/agent/AgentOrchestrator.java`

- [ ] **Step 1: Write failing tests** that construct an orchestrator with test telemetry and prove success, RAG reject, tool reject, pending action, and shadow fallback are reported exactly once.
- [ ] **Step 2: Run** the two targeted test classes and confirm the new assertions fail because the orchestrator does not call telemetry.
- [ ] **Step 3: Add telemetry to the Spring constructor and internal factories**, defaulting static factories to `AgentTelemetry.noop()` and adding a telemetry-aware test factory.
- [ ] **Step 4: Wrap `handle` with the request timer** and report RAG, tool, pending-action, and shadow outcomes at their typed decision points.
- [ ] **Step 5: Re-run** the targeted tests and the pre-existing orchestrator/tool suites.

### Task 3: Add Actuator and explicit privacy configuration

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/com/tzq/ticketops/observability/ObservabilityEndpointTest.java`

- [ ] **Step 1: Write a failing Spring Boot test** for `/actuator/health`, `/actuator/metrics/ticketops.agent.request`, and non-exposure of `/actuator/env`.
- [ ] **Step 2: Run** `mvn test "-Dtest=ObservabilityEndpointTest"` and confirm failure because Actuator is absent.
- [ ] **Step 3: Add `spring-boot-starter-actuator`** and expose only `health,info,metrics`.
- [ ] **Step 4: Explicitly set false** for `spring.ai.chat.client.observations.log-prompt`, `log-completion`, `spring.ai.chat.observations.log-prompt`, `log-completion`, `include-error-logging`, `spring.ai.tools.observations.include-content`, and `spring.ai.vectorstore.observations.log-query-response`.
- [ ] **Step 5: Re-run** the endpoint test and confirm it passes.

### Task 4: Pin documentation and repository contracts

**Files:**
- Create: `docs/observability/privacy-safe-observability.md`
- Create: `src/test/java/com/tzq/ticketops/eval/ObservabilityArtifactsTest.java`
- Modify: `README.md`

- [ ] **Step 1: Write a failing artifact test** for dependency, endpoint, metric names, privacy switches, README entry, and boundaries.
- [ ] **Step 2: Run** `mvn test "-Dtest=ObservabilityArtifactsTest"` and confirm the documentation assertions fail.
- [ ] **Step 3: Add the observability guide and README section** with local curl/PowerShell examples and explicit non-goals.
- [ ] **Step 4: Re-run** the artifact test and confirm it passes.

### Task 5: Full verification and phase closeout

**Files:**
- Modify only if verification exposes a scoped defect.

- [ ] **Step 1: Run** `mvn test` and confirm every test passes.
- [ ] **Step 2: Run** `mvn package -DskipTests` and inspect the executable JAR.
- [ ] **Step 3: Run** `scripts\accept.ps1` and confirm the full acceptance suite and security gates pass.
- [ ] **Step 4: Start the application**, execute the five live scenarios, query `/actuator/metrics/ticketops.agent.request`, and verify no sensitive marker appears in the metrics payload.
- [ ] **Step 5: Run strict repository and Obsidian secret scans** plus `git diff --check`.
- [ ] **Step 6: Perform an independent diff review**, fix any actionable findings through red-green tests, then push a PR.
- [ ] **Step 7: Merge after green checks**, rerun main acceptance, and update project/module/knowledge/Daily Obsidian notes without secrets.
