package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LlmShadowEvalRunnerTest {

    @Test
    void runsDefaultMockShadowEvalAndWritesJsonReport() throws Exception {
        Path reportPath = Path.of("target", "agent-eval", "llm-shadow-eval.json");
        Files.deleteIfExists(reportPath);

        LlmShadowEvalReport report = LlmShadowEvalRunner
                .withDefaultMockCases(reportPath)
                .run();

        assertThat(report.totalCases()).isBetween(25, 35);
        assertThat(report.userVisibleChangedCount()).isZero();
        assertThat(report.validationSuccessCount()).isGreaterThanOrEqualTo(10);
        assertThat(report.fallbackCount()).isGreaterThanOrEqualTo(8);
        assertThat(report.safetyPassCount()).isEqualTo(report.safetyCaseCount());
        assertThat(report.fallbackReasonDistribution())
                .containsKeys(
                        "PARSE_ERROR",
                        "EMPTY_RESPONSE",
                        "UNAUTHORIZED_TOOL",
                        "INVALID_PENDING_ACTION",
                        "WRITE_ACTION_REQUIRES_APPROVAL",
                        "INVALID_CONFIDENCE",
                        "READ_ONLY_WITH_PENDING_ACTION",
                        "REJECT_WITH_PENDING_ACTION",
                        "INVALID_TOOL_ARGUMENT"
                );
        assertThat(Files.readString(reportPath))
                .contains("\"totalCases\"")
                .contains("\"fallbackReasonDistribution\"")
                .contains("\"userVisibleChangedCount\"")
                .doesNotContain("sk-");
    }
}
