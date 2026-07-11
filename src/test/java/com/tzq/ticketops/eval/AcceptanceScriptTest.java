package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AcceptanceScriptTest {

    @Test
    void acceptScriptGeneratesMarkdownReportFromShadowEvalJson() throws Exception {
        Path evalReportPath = Path.of("target", "agent-eval", "llm-shadow-eval.json");
        Path acceptanceReportPath = Path.of("target", "agent-eval", "acceptance-report.md");
        Files.deleteIfExists(acceptanceReportPath);
        LlmShadowEvalRunner.withDefaultMockCases(evalReportPath).run();

        Path scriptPath = Path.of("scripts", "accept.ps1");
        assertThat(scriptPath).exists();

        ProcessResult result = run(List.of(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptPath.toString(),
                "-SkipMavenTest"
        ));

        assertThat(result.exitCode())
                .as(result.output())
                .isZero();
        assertThat(result.output()).contains("acceptance-report.md");
        assertThat(Files.readString(acceptanceReportPath))
                .contains("# TicketOpsAgent Acceptance Report")
                .contains("mvn test: SKIPPED")
                .contains("Secret scan: PASS")
                .contains("totalCases: 34")
                .contains("validationSuccessCount: 11")
                .contains("fallbackCount: 23")
                .contains("safetyPassCount: 9 of 9")
                .contains("traceAuditPassCount: 34 of 34")
                .contains("userVisibleChangedCount: 0")
                .contains("userRiskLevel is the deterministic baseline")
                .contains("shadowRiskLevel is the LLM shadow candidate")
                .contains("Current boundaries")
                .contains("No real enterprise system integration")
                .doesNotContain("sk-");
    }

    @Test
    void acceptScriptReportsSkippedLiveDeepSeekWhenIncludedWithoutApiKey() throws Exception {
        Path evalReportPath = Path.of("target", "agent-eval", "llm-shadow-eval.json");
        Path acceptanceReportPath = Path.of("target", "agent-eval", "acceptance-report.md");
        Files.deleteIfExists(acceptanceReportPath);
        LlmShadowEvalRunner.withDefaultMockCases(evalReportPath).run();

        ProcessResult result = run(
                List.of(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-File",
                        Path.of("scripts", "accept.ps1").toString(),
                        "-SkipMavenTest",
                        "-IncludeLiveDeepSeek"
                ),
                Map.of("DEEPSEEK_API_KEY", "")
        );

        assertThat(result.exitCode())
                .as(result.output())
                .isZero();
        assertThat(Files.readString(acceptanceReportPath))
                .contains("Live DeepSeek: SKIPPED")
                .contains("provider: deepseek")
                .contains("model: deepseek-v4-flash")
                .contains("promptVersion: deepseek-shadow-v2")
                .contains("schemaVersion: agent-decision-v1")
                .contains("reason: DEEPSEEK_API_KEY not set")
                .contains("userRiskLevel is the deterministic baseline")
                .contains("shadowRiskLevel is the LLM shadow candidate")
                .doesNotContain("sk-");
    }

    private ProcessResult run(List<String> command) throws Exception {
        return run(command, Map.of());
    }

    private ProcessResult run(List<String> command, Map<String, String> environmentOverrides) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        processBuilder.environment().putAll(environmentOverrides);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
