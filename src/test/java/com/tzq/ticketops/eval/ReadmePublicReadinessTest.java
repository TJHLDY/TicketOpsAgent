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
                .contains("No DeepSeek API key is required for the default local demo")
                .contains("scripts\\demo-backend-api.ps1")
                .contains("## Architecture Overview")
                .contains("/api/agent/chat")
                .contains("Deterministic Decision Service")
                .contains("DeepSeek Shadow Candidate")
                .contains("## Validation Evidence")
                .contains("55 tests PASS")
                .contains("local validation evidence, not a production SLA")
                .contains("## Demo Data")
                .contains("mock-user-001")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("Shadow eval: 34 cases")
                .contains("Safety cases: 9/9")
                .contains("Trace audit: 34/34")
                .contains("User-visible changed count: 0")
                .contains("## What This Project Does Not Claim")
                .contains("production AI Agent")
                .contains("real account unlock")
                .contains("LLM main decisioning")
                .contains("## Roadmap")
                .contains("Lightweight static demo console")
                .contains("## License")
                .contains("MIT License");
    }
}
