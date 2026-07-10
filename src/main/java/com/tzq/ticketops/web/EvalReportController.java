package com.tzq.ticketops.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/eval/reports")
public class EvalReportController {

    private final ObjectMapper objectMapper;
    private final Path reportDir;

    public EvalReportController(
            ObjectMapper objectMapper,
            @Value("${ticketops.eval.report-dir:target/agent-eval}") String reportDir
    ) {
        this.objectMapper = objectMapper;
        this.reportDir = Path.of(reportDir);
    }

    @GetMapping("/latest")
    public EvalReportDto latest() {
        Path acceptanceReport = reportDir.resolve("acceptance-report.md");
        Path shadowEvalReport = reportDir.resolve("llm-shadow-eval.json");
        boolean acceptancePresent = Files.isRegularFile(acceptanceReport);
        boolean shadowPresent = Files.isRegularFile(shadowEvalReport);
        if (!acceptancePresent && !shadowPresent) {
            return EvalReportDto.empty();
        }

        try {
            String acceptanceText = acceptancePresent ? Files.readString(acceptanceReport) : "";
            JsonNode shadowEval = shadowPresent ? objectMapper.readTree(Files.readString(shadowEvalReport)) : objectMapper.createObjectNode();
            return new EvalReportDto(
                    true,
                    gateValue(acceptanceText, "mvn test"),
                    gateValue(acceptanceText, "Secret scan"),
                    gateValue(acceptanceText, "Shadow eval report"),
                    gateValue(acceptanceText, "Live DeepSeek"),
                    intValue(shadowEval, "totalCases"),
                    intValue(shadowEval, "parseSuccessCount"),
                    intValue(shadowEval, "validationSuccessCount"),
                    intValue(shadowEval, "fallbackCount"),
                    intValue(shadowEval, "safetyPassCount"),
                    intValue(shadowEval, "safetyCaseCount"),
                    intValue(shadowEval, "traceAuditPassCount"),
                    intValue(shadowEval, "userVisibleChangedCount")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read eval report", exception);
        }
    }

    private String gateValue(String report, String label) {
        String prefix = "- " + label + ": ";
        return report.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .findFirst()
                .orElse("UNKNOWN");
    }

    private Integer intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    public record EvalReportDto(
            boolean available,
            String mavenTest,
            String secretScan,
            String shadowEvalReport,
            String liveDeepSeek,
            Integer totalCases,
            Integer parseSuccessCount,
            Integer validationSuccessCount,
            Integer fallbackCount,
            Integer safetyPassCount,
            Integer safetyCaseCount,
            Integer traceAuditPassCount,
            Integer userVisibleChangedCount
    ) {
        static EvalReportDto empty() {
            return new EvalReportDto(
                    false,
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
