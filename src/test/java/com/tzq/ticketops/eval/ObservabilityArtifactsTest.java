package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityArtifactsTest {

    @Test
    void configurationPinsActuatorSurfaceAndSensitiveContentDefaults() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        String application = Files.readString(Path.of("src", "main", "resources", "application.yml"));

        assertThat(pom).contains("<artifactId>spring-boot-starter-actuator</artifactId>");
        assertThat(application)
                .contains("include: health,info,metrics")
                .contains("log-prompt: false")
                .contains("log-completion: false")
                .contains("include-error-logging: false")
                .contains("include-content: false")
                .contains("log-query-response: false");
    }

    @Test
    void guideDocumentsMetricsPrivacyAndLocalInspectionBoundary() throws Exception {
        Path guidePath = Path.of("docs", "observability", "privacy-safe-observability.md");

        assertThat(guidePath).exists();
        assertThat(Files.readString(guidePath))
                .contains("# Privacy-Safe Observability")
                .contains("ticketops.agent.request")
                .contains("ticketops.rag.retrieval")
                .contains("ticketops.tool.execution")
                .contains("ticketops.pending.action")
                .contains("ticketops.shadow.decision")
                .contains("/actuator/metrics")
                .contains("low-cardinality")
                .contains("requester ID")
                .contains("tool arguments")
                .contains("prompt")
                .contains("No Prometheus")
                .contains("local diagnostics");
    }

    @Test
    void readmeExposesObservabilityCapabilityAndBoundaries() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## Privacy-Safe Observability")
                .contains("Invoke-RestMethod http://localhost:8080/actuator/health")
                .contains("Invoke-RestMethod http://localhost:8080/actuator/metrics/ticketops.agent.request")
                .contains("prompt and completion content remain disabled")
                .contains("No Prometheus, Grafana, or external tracing backend");
    }
}
