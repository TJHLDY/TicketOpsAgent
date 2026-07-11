package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackendMvpCompletionArtifactsTest {

    @Test
    void completionReportMapsInitialMvpToEvidenceAndBoundaries() throws Exception {
        String report = Files.readString(Path.of("docs", "completion", "backend-mvp-completion-report.md"));

        assertThat(report)
                .contains("# Backend MVP Completion Report")
                .contains("Overall verdict: COMPLETE")
                .contains("Ticket creation")
                .contains("Classification and risk")
                .contains("SOP RAG")
                .contains("Read-only tools")
                .contains("Draft persistence")
                .contains("Pending actions")
                .contains("Audit and observability")
                .contains("Evaluation and reproducibility")
                .contains("Docker Compose")
                .contains("Initial Spike Checklist")
                .contains("133 tests")
                .contains("Not production");
    }

    @Test
    void readmeLinksTheCompletionReportWithoutExpandingClaims() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## Backend MVP Completion")
                .contains("docs/completion/backend-mvp-completion-report.md")
                .contains("mock-only")
                .contains("shadow-only")
                .contains("not a production ITSM system");
    }
}
