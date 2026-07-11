package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ControlledToolExecutionArtifactsTest {

    @Test
    void guideDocumentsTrustBoundaryValidationBudgetAndRejection() throws Exception {
        Path guidePath = Path.of("docs", "tools", "controlled-tool-execution.md");

        assertThat(guidePath).exists();
        assertThat(Files.readString(guidePath))
                .contains("# Controlled Read-Only Tool Execution")
                .contains("untrusted proposal")
                .contains("getAccountStatus")
                .contains("getUserPermissions")
                .contains("exact argument")
                .contains("requester")
                .contains("max-calls-per-request")
                .contains("TOOL_DECISION")
                .contains("TOOL_REJECT")
                .contains("mock-only")
                .contains("No write tool");
    }

    @Test
    void readmeExposesControlledToolBoundaryAndCurrentEvidence() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## Controlled Read-Only Tool Execution")
                .contains("133 tests PASS")
                .contains("ticketops.tools.max-calls-per-request")
                .contains("requester identity")
                .contains("zero successful tool calls and zero pending actions");
    }
}
