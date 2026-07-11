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
                .contains("Java 21")
                .contains("Spring Boot 4.1.0")
                .contains("Spring AI 2.0.0")
                .contains("VectorStoreRetriever")
                .contains("offline feature-hash embedding")
                .contains("local ONNX Transformers")
                .contains("low-similarity refusal")
                .contains("SimpleVectorStore")
                .contains("deterministic ticket triage")
                .contains("DeepSeek shadow candidate evaluation")
                .contains("API error contract hardening")
                .contains("## Quick Start")
                .contains("No DeepSeek API key is required for the default local demo")
                .contains("scripts\\demo-backend-api.ps1")
                .contains("## Architecture Overview")
                .contains("/api/agent/chat")
                .contains("Deterministic Decision Service")
                .contains("DeepSeek Shadow Candidate")
                .contains("## Scenario Acceptance")
                .contains("ScenarioAcceptanceTest")
                .contains("docs/scenarios/scenario-playbook.md")
                .contains("## Scenario Demo Report")
                .contains("scripts\\demo-scenarios.ps1")
                .contains("docs/scenarios/scenario-report-guide.md")
                .contains("docs/scenarios/reproducibility-notes.md")
                .contains("## Validation Evidence")
                .contains("119 tests PASS")
                .contains("## Controlled Read-Only Tool Execution")
                .contains("TOOL_REJECT")
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
                .contains("Scenario acceptance suite")
                .contains("## License")
                .contains("MIT License");
    }
}
