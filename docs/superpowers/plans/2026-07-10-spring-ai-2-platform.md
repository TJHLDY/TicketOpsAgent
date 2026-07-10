# Spring AI 2 Platform Baseline Implementation Plan

> **For agentic workers:** Implement this plan task-by-task in the current branch. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate TicketOpsAgent to Java 21, Spring Boot 4.1.0, and Spring AI 2.0.0 without changing the existing MVP behavior or safety boundaries.

**Architecture:** This is an isolated platform migration. A file-level contract test proves the selected dependency baseline, while the existing integration, scenario, and acceptance suites prove behavioral compatibility. AI behavior remains deterministic by default and DeepSeek remains shadow-only.

**Tech Stack:** Java 21 bytecode, Maven, Spring Boot 4.1.0, Spring AI 2.0.0, Spring JDBC, H2, PostgreSQL driver, JUnit 5, AssertJ.

---

### Task 1: Add A Failing Platform Contract

**Files:**
- Create: `src/test/java/com/tzq/ticketops/eval/PlatformBaselineContractTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformBaselineContractTest {

    @Test
    void pomUsesSpringAi2PlatformBaseline() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));

        assertThat(pom)
                .contains("<version>4.1.0</version>")
                .contains("<java.version>21</java.version>")
                .contains("<spring-ai.version>2.0.0</spring-ai.version>");
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn test "-Dtest=PlatformBaselineContractTest"`

Expected: FAIL because `pom.xml` still contains Spring Boot 3.5.15, Java 17, and Spring AI 1.1.8.

### Task 2: Migrate The Platform Versions

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Change only the three platform versions**

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.0</version>
    <relativePath/>
</parent>

<properties>
    <java.version>21</java.version>
    <spring-ai.version>2.0.0</spring-ai.version>
</properties>
```

- [ ] **Step 2: Run the targeted contract and compile path**

Run: `mvn test "-Dtest=PlatformBaselineContractTest"`

Expected: the contract passes and Maven compiles the application on the new dependency graph. If compilation exposes a removed API, make only the smallest source change required by the Spring AI 2 upgrade notes.

### Task 3: Update Public Technology Evidence

**Files:**
- Modify: `README.md`
- Modify: `src/test/java/com/tzq/ticketops/eval/ReadmePublicReadinessTest.java`

- [ ] **Step 1: Extend the README contract**

Add these exact assertions:

```java
.contains("Java 21")
.contains("Spring Boot 4.1.0")
.contains("Spring AI 2.0.0")
```

- [ ] **Step 2: Run the README test and verify RED**

Run: `mvn test "-Dtest=ReadmePublicReadinessTest"`

Expected: FAIL because the README still describes the old baseline.

- [ ] **Step 3: Update the README technology stack**

```markdown
- Java 21.
- Spring Boot 4.1.0.
- Spring AI 2.0.0 BOM with DeepSeek starter support.
```

- [ ] **Step 4: Run the README test and verify GREEN**

Run: `mvn test "-Dtest=ReadmePublicReadinessTest"`

Expected: PASS.

### Task 4: Run Behavioral Regression And Runtime Acceptance

**Files:**
- Modify only source or configuration files proven necessary by a failing check.

- [ ] **Step 1: Run the complete test suite**

Run: `mvn test`

Expected: 80 tests pass after adding `PlatformBaselineContractTest`.

- [ ] **Step 2: Build the executable artifact**

Run: `mvn package`

Expected: BUILD SUCCESS and a runnable jar under `target/`.

- [ ] **Step 3: Run repository acceptance**

Run: `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File scripts\accept.ps1`

Expected: Maven, secret scan, and shadow eval gates pass with live DeepSeek disabled.

- [ ] **Step 4: Run a default-profile HTTP smoke**

Start the application on a free local port, call `POST /api/agent/chat` with the existing account-locked scenario, verify the response includes `ticketId`, `ACCOUNT_LOCKED`, and `NOT_EXECUTED_MOCK_ONLY` pending-action evidence, then stop the process and verify the port is free.

### Task 5: Close The Migration Evidence

**Files:**
- Modify: `README.md`
- Modify: `src/test/java/com/tzq/ticketops/eval/ReadmePublicReadinessTest.java`

- [ ] **Step 1: Update validation evidence to 80 tests**

Use the exact README text: ``- `mvn test`: 80 tests PASS``.

- [ ] **Step 2: Run final checks**

Run the complete tests, `scripts\accept.ps1`, `git diff --check`, and `git status --short`.

Expected: all tests and acceptance gates pass; the diff check reports no errors; only planned files are modified.

- [ ] **Step 3: Scan for secrets**

Run a strict repository scan for API-key-shaped values while excluding `.git` and `target`. Expected: no matches. Keep only `DEEPSEEK_API_KEY` as an environment-variable name in documentation and configuration.

