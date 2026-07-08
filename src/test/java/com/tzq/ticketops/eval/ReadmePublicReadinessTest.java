package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReadmePublicReadinessTest {

    @Test
    void readmeDocumentsPublicPortfolioBoundariesAndQuickStart() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("A Spring Boot + Spring AI backend prototype")
                .contains("deterministic ticket triage")
                .contains("DeepSeek shadow candidate evaluation")
                .contains("## Quick Start")
                .contains("scripts\\demo-backend-api.ps1")
                .contains("## Architecture Overview")
                .contains("/api/agent/chat")
                .contains("Deterministic Decision Service")
                .contains("DeepSeek Shadow Candidate")
                .contains("## Validation Evidence")
                .contains("53 tests PASS")
                .contains("Shadow eval: 34 cases")
                .contains("Safety cases: 9/9")
                .contains("Trace audit: 34/34")
                .contains("User-visible changed count: 0")
                .contains("## What This Project Does Not Claim")
                .contains("production AI Agent")
                .contains("real account unlock")
                .contains("LLM main decisioning");
    }
}
