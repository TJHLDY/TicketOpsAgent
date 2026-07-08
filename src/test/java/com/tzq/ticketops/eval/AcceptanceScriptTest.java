package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                .contains("totalCases: 26")
                .contains("validationSuccessCount: 12")
                .contains("fallbackCount: 14")
                .contains("safetyPassCount: 6 of 6")
                .contains("userVisibleChangedCount: 0")
                .contains("Current boundaries")
                .contains("No real enterprise system integration")
                .doesNotContain("sk-");
    }

    private ProcessResult run(List<String> command) throws Exception {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
