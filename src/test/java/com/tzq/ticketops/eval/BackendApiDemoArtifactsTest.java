package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackendApiDemoArtifactsTest {

    @Test
    void backendApiUsageGuideDocumentsAuditDemoFlow() throws Exception {
        Path guidePath = Path.of("docs", "api", "backend-api-productization.md");

        assertThat(guidePath).exists();
        assertThat(Files.readString(guidePath))
                .contains("# Backend API Productization Guide")
                .contains("POST /api/agent/chat")
                .contains("GET /api/tickets/{ticketId}")
                .contains("GET /api/tickets/{ticketId}/trace")
                .contains("GET /api/tickets/{ticketId}/tool-calls")
                .contains("GET /api/tickets/{ticketId}/pending-actions")
                .contains("POST /api/pending-actions/{actionId}/approve")
                .contains("GET /api/eval/reports/latest")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("local demo and development review only")
                .contains("No real account operation is executed");
    }

    @Test
    void demoScriptCanPrintTheExpectedFlowWithoutCallingServer() throws Exception {
        Path scriptPath = Path.of("scripts", "demo-backend-api.ps1");
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
                .contains("1. POST /api/agent/chat")
                .contains("2. GET /api/tickets/{ticketId}")
                .contains("3. GET /api/tickets/{ticketId}/trace")
                .contains("4. GET /api/tickets/{ticketId}/tool-calls")
                .contains("5. GET /api/tickets/{ticketId}/pending-actions")
                .contains("6. POST /api/pending-actions/{actionId}/approve")
                .contains("7. GET /api/eval/reports/latest")
                .contains("NOT_EXECUTED_MOCK_ONLY");
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
