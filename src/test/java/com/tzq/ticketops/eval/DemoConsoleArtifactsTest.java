package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoConsoleArtifactsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void demoConsoleArtifactsExistAndDescribeAuditOnlyBoundaries() throws Exception {
        String html = readClasspath("static/demo-console.html");
        String js = readClasspath("static/demo-console.js");
        String css = readClasspath("static/demo-console.css");
        String readme = Files.readString(Path.of("README.md"));

        assertThat(html)
                .contains("demo-console.css")
                .contains("demo-console.js")
                .contains("TicketOpsAgent Demo Console")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("User-facing result is produced by the deterministic main flow.")
                .contains("It does not execute real enterprise operations.")
                .contains("Approve Review")
                .contains("Reject Review")
                .contains("Approve Review / Reject Review never unlock accounts, reset passwords, grant permissions, dispatch tickets, or close tickets.");

        assertThat(js)
                .contains("/api/agent/chat")
                .contains("/api/tickets/")
                .contains("/api/pending-actions/")
                .contains("/api/eval/reports/latest")
                .contains("approve")
                .contains("reject")
                .contains("formatApiError")
                .contains("errorCode")
                .contains("status");
        assertThat(js).doesNotContain("alert(");

        assertThat(css)
                .contains(".trace-timeline")
                .contains(".boundary-panel");

        assertThat(readme)
                .contains("## Lightweight Demo Console")
                .contains("http://localhost:8080/demo-console.html");
    }

    @Test
    void demoConsoleHtmlIsServedAsStaticResource() throws Exception {
        mockMvc.perform(get("/demo-console.html"))
                .andExpect(status().isOk());
    }

    private String readClasspath(String location) throws Exception {
        ClassPathResource resource = new ClassPathResource(location);
        assertThat(resource.exists()).as(location + " should exist").isTrue();
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }
}
