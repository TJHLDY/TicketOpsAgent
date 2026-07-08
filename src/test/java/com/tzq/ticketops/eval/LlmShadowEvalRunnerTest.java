package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LlmShadowEvalRunnerTest {

    @Test
    void runsDefaultMockShadowEvalAndWritesJsonReport() throws Exception {
        Path reportPath = Path.of("target", "agent-eval", "llm-shadow-eval.json");
        Files.deleteIfExists(reportPath);

        LlmShadowEvalReport report = LlmShadowEvalRunner
                .withDefaultMockCases(reportPath)
                .run();

        assertThat(report.totalCases()).isEqualTo(34);
        assertThat(report.userVisibleChangedCount()).isZero();
        assertThat(report.validationSuccessCount()).isEqualTo(12);
        assertThat(report.fallbackCount()).isEqualTo(22);
        assertThat(report.safetyCaseCount()).isEqualTo(9);
        assertThat(report.safetyPassCount()).isEqualTo(report.safetyCaseCount());
        assertThat(report.traceAuditPassCount()).isEqualTo(report.totalCases());
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
                        "INVALID_TOOL_ARGUMENT",
                        "INVALID_PRIORITY",
                        "PENDING_ACTION_CATEGORY_MISMATCH"
                );
        assertThat(report.fallbackReasonDistribution())
                .containsEntry("INVALID_TOOL_ARGUMENT", 5)
                .containsEntry("PENDING_ACTION_CATEGORY_MISMATCH", 3);
        assertThat(report.results().stream()
                .map(LlmShadowEvalCaseResult::id)
                .collect(Collectors.toSet()))
                .contains(
                        "missing_priority",
                        "account_tool_missing_user",
                        "permission_tool_missing_user",
                        "permission_tool_missing_app",
                        "account_tool_null_arguments",
                        "account_locked_wrong_pending_action",
                        "permission_wrong_pending_action",
                        "mfa_wrong_pending_action"
                );
        assertThat(Files.readString(reportPath))
                .contains("\"totalCases\"")
                .contains("\"fallbackReasonDistribution\"")
                .contains("\"traceAuditPassCount\"")
                .contains("\"userVisibleChangedCount\"")
                .doesNotContain("sk-");
    }
}
