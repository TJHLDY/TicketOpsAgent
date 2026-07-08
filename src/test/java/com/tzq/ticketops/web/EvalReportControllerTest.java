package com.tzq.ticketops.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ticketops.eval.report-dir=target/test-agent-eval")
class EvalReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    private final Path reportDir = Path.of("target", "test-agent-eval");

    @BeforeEach
    void writeReports() throws Exception {
        Files.createDirectories(reportDir);
        Files.writeString(reportDir.resolve("acceptance-report.md"), """
                # TicketOpsAgent Acceptance Report

                ## Gates

                - mvn test: PASS
                - Secret scan: PASS
                - Shadow eval report: PASS
                - Live DeepSeek: DISABLED
                """);
        Files.writeString(reportDir.resolve("llm-shadow-eval.json"), """
                {
                  "totalCases": 34,
                  "parseSuccessCount": 31,
                  "validationSuccessCount": 12,
                  "fallbackCount": 22,
                  "safetyPassCount": 9,
                  "safetyCaseCount": 9,
                  "traceAuditPassCount": 34,
                  "userVisibleChangedCount": 0
                }
                """);
    }

    @Test
    void latestEvalReportReturnsAcceptanceAndShadowMetrics() throws Exception {
        mockMvc.perform(get("/api/eval/reports/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.mavenTest").value("PASS"))
                .andExpect(jsonPath("$.secretScan").value("PASS"))
                .andExpect(jsonPath("$.shadowEvalReport").value("PASS"))
                .andExpect(jsonPath("$.liveDeepSeek").value("DISABLED"))
                .andExpect(jsonPath("$.totalCases").value(34))
                .andExpect(jsonPath("$.parseSuccessCount").value(31))
                .andExpect(jsonPath("$.validationSuccessCount").value(12))
                .andExpect(jsonPath("$.fallbackCount").value(22))
                .andExpect(jsonPath("$.safetyPassCount").value(9))
                .andExpect(jsonPath("$.safetyCaseCount").value(9))
                .andExpect(jsonPath("$.traceAuditPassCount").value(34))
                .andExpect(jsonPath("$.userVisibleChangedCount").value(0));
    }
}
