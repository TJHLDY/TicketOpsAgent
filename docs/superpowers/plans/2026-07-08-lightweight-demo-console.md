# Lightweight Static Demo Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a local static demo console that visualizes the existing TicketOpsAgent backend chain without adding new business capabilities.

**Architecture:** Use Spring Boot static resources under `src/main/resources/static`. Vanilla HTML/CSS/JS calls existing REST APIs and renders the current ticket, trace, tool calls, pending actions, and eval report. The console is a portfolio/demo surface only; pending action review remains audit-only.

**Tech Stack:** Spring Boot static resources, vanilla JavaScript `fetch`, JUnit artifact/resource tests, existing Maven and acceptance script.

---

## File Structure

- Create `src/main/resources/static/demo-console.html`
  - Owns the page skeleton, form, sample buttons, output sections, and boundary copy.
- Create `src/main/resources/static/demo-console.css`
  - Owns the compact operations-console visual style, responsive layout, status chips, timeline, and audit-only notices.
- Create `src/main/resources/static/demo-console.js`
  - Owns sample filling, API calls, rendering, pending action review, refresh flow, and defensive formatting for missing fields.
- Create `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`
  - Verifies static assets, required copy, API endpoint references, README entry, and Spring static resource smoke.
- Modify `README.md`
  - Add a short “Lightweight Demo Console” section and mark the roadmap item complete.
- Optionally modify `docs/api/backend-api-productization.md`
  - Add a one-line local demo console entry if it fits naturally.

## Task 1: Artifact Test

**Files:**
- Create: `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`
- Test: `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoConsoleArtifactsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoConsoleArtifactsExistAndDescribeAuditOnlyBoundaries() throws Exception {
        String html = read("static/demo-console.html");
        String js = read("static/demo-console.js");
        String css = read("static/demo-console.css");
        String readme = readFromProjectRoot("README.md");

        assertThat(html).contains("demo-console.css", "demo-console.js");
        assertThat(html).contains("TicketOpsAgent Demo Console");
        assertThat(html).contains("NOT_EXECUTED_MOCK_ONLY");
        assertThat(html).contains("User-facing result is produced by the deterministic main flow.");
        assertThat(html).contains("It does not execute real enterprise operations.");

        assertThat(js).contains("/api/agent/chat");
        assertThat(js).contains("/api/tickets/");
        assertThat(js).contains("/api/pending-actions/");
        assertThat(js).contains("/api/eval/reports/latest");
        assertThat(js).contains("approve");
        assertThat(js).contains("reject");

        assertThat(css).contains(".trace-timeline");
        assertThat(css).contains(".boundary-panel");

        assertThat(readme).contains("## Lightweight Demo Console");
        assertThat(readme).contains("http://localhost:8080/demo-console.html");
    }

    @Test
    void demoConsoleHtmlIsServedAsStaticResource() throws Exception {
        mockMvc.perform(get("/demo-console.html"))
                .andExpect(status().isOk());
    }

    private String read(String classpathLocation) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        assertThat(resource.exists()).isTrue();
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private String readFromProjectRoot(String path) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: FAIL because `static/demo-console.html`, `static/demo-console.js`, and `static/demo-console.css` do not exist.

## Task 2: Static Assets

**Files:**
- Create: `src/main/resources/static/demo-console.html`
- Create: `src/main/resources/static/demo-console.css`
- Create: `src/main/resources/static/demo-console.js`
- Test: `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`

- [ ] **Step 1: Implement minimal static page**

Create a single-page local console with:

- title/description/requester form
- five sample buttons: OA account locked, CRM permission request, VPN MFA issue, Prompt injection / admin request, Non-IT request
- submit button calling `POST /api/agent/chat`
- current ticket summary
- ticket detail panel with refresh button
- trace timeline
- tool calls panel
- pending action panel with `Approve Review`, `Reject Review`, and refresh
- eval report summary
- boundary copy that review is audit-only and deterministic remains user-facing

- [ ] **Step 2: Run the artifact test to verify it passes**

Run:

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: PASS.

## Task 3: README Entry

**Files:**
- Modify: `README.md`
- Test: `src/test/java/com/tzq/ticketops/eval/DemoConsoleArtifactsTest.java`

- [ ] **Step 1: Add README section**

Add:

```markdown
## Lightweight Demo Console

Start the app:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080/demo-console.html
```

The console demonstrates ticket creation through `/api/agent/chat`, ticket detail lookup, trace timeline, read-only tool call evidence, pending action review with `NOT_EXECUTED_MOCK_ONLY`, and eval report summary.

The console is a local static demo page only. It does not add login, RBAC, real enterprise integrations, real execution, LLM main decisioning, hybrid mode, or production RAG.
```

Update Roadmap:

```markdown
- [x] Lightweight static demo console
```

- [ ] **Step 2: Run the artifact test**

Run:

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest"
```

Expected: PASS.

## Task 4: Full Verification

**Files:**
- Verify all touched files.

- [ ] **Step 1: Run targeted tests**

```powershell
mvn test "-Dtest=DemoConsoleArtifactsTest,ReadmePublicReadinessTest,RepositoryPublicHygieneTest"
```

- [ ] **Step 2: Run full tests**

```powershell
mvn test
```

- [ ] **Step 3: Run acceptance gate**

```powershell
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1
```

- [ ] **Step 4: Run local static resource smoke**

```powershell
mvn spring-boot:run
# Open http://localhost:8080/demo-console.html
```

Expected: page loads and supports the demo flow:

1. Fill OA account locked sample.
2. Submit ticket.
3. See `ACCOUNT_LOCKED`, `NEEDS_APPROVAL`, ticket detail, trace, `getAccountStatus`, `LOCKED`, and `UNLOCK_ACCOUNT`.
4. Approve review.
5. See `APPROVED` and `NOT_EXECUTED_MOCK_ONLY`.
6. See eval report summary when the acceptance report exists.

## Self-Review

- Spec coverage: covers the five required areas, sample buttons, audit-only pending action review, eval summary, README entry, and static resource test.
- Placeholder scan: no unfinished placeholder markers.
- Type consistency: uses existing endpoint names and response fields from current controllers.
