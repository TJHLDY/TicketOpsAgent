package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioDemoArtifactsTest {

    @Test
    void scenarioDemoScriptPrintsPlanWithoutCallingServer() throws Exception {
        Path scriptPath = Path.of("scripts", "demo-scenarios.ps1");
        assertThat(scriptPath).exists();

        ProcessResult result = run(List.of(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                scriptPath.toString(),
                "-ShowPlan"
        ));

        assertThat(result.exitCode())
                .as(result.output())
                .isZero();
        assertThat(result.output())
                .contains("TicketOpsAgent scenario demo flow")
                .contains("1. OA account locked")
                .contains("2. CRM permission request")
                .contains("3. VPN MFA issue")
                .contains("4. Prompt injection rejection")
                .contains("5. Non-IT request rejection")
                .contains("target/scenario-acceptance/scenario-report.json")
                .contains("target/scenario-acceptance/scenario-report.md")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("No real enterprise operation is executed");
    }

    @Test
    void scenarioReportGuideDocumentsBoundariesAndReportShape() throws Exception {
        Path guidePath = Path.of("docs", "scenarios", "scenario-report-guide.md");

        assertThat(guidePath).exists();
        assertThat(Files.readString(guidePath))
                .contains("# Scenario Report Guide")
                .contains("scripts/demo-scenarios.ps1")
                .contains("target/scenario-acceptance/scenario-report.json")
                .contains("target/scenario-acceptance/scenario-report.md")
                .contains("totalScenarios")
                .contains("passedScenarios")
                .contains("failedScenarios")
                .contains("scenarioResults")
                .contains("oa-account-locked")
                .contains("crm-permission-request")
                .contains("vpn-mfa-issue")
                .contains("prompt-injection-rejection")
                .contains("non-it-request-rejection")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("No real LDAP / SSO / IAM / OA / ITSM integration")
                .contains("No LLM main / hybrid routing")
                .contains("No pgvector / production RAG");
    }

    @Test
    void scenarioDemoScriptUsesRunIdAndTicketIdFirstBinding() throws Exception {
        String script = Files.readString(Path.of("scripts", "demo-scenarios.ps1"));

        assertThat(script)
                .contains("scenarioRunId")
                .contains("Resolve-TicketId")
                .contains("$agentResponse.ticketId")
                .contains("runId = $RunId")
                .contains("ticketIds");
    }

    @Test
    void reproducibilityNotesDocumentRepeatableLocalRuns() throws Exception {
        Path notesPath = Path.of("docs", "scenarios", "reproducibility-notes.md");

        assertThat(notesPath).exists();
        assertThat(Files.readString(notesPath))
                .contains("# Scenario Demo Reproducibility Notes")
                .contains("ticketId returned by `POST /api/agent/chat`")
                .contains("scenarioRunId")
                .contains("Run the script twice")
                .contains("5 passed / 0 failed")
                .contains("target/scenario-acceptance")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("No real LDAP / SSO / IAM / OA / ITSM integration")
                .contains("No LLM main / hybrid routing")
                .contains("No pgvector / production RAG");
    }

    @Test
    void readmeLinksScenarioReportGuide() throws Exception {
        assertThat(Files.readString(Path.of("README.md")))
                .contains("## Scenario Demo Report")
                .contains("scripts\\demo-scenarios.ps1")
                .contains("docs/scenarios/scenario-report-guide.md")
                .contains("docs/scenarios/reproducibility-notes.md");
    }

    private ProcessResult run(List<String> command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new ProcessResult(process.waitFor(), output);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
