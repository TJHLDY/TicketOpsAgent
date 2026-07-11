package com.tzq.ticketops.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0"
)
class ObservabilityEndpointTest {

    @LocalServerPort
    int applicationPort;

    @LocalManagementPort
    int managementPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void exposesActuatorOnlyOnSeparateManagementServer() throws Exception {
        HttpResponse<String> chat = send("POST", applicationPort, "/api/agent/chat", """
                {
                  "requesterId": "mock-user-001",
                  "title": "OA login failed",
                  "description": "The account is locked."
                }
                """);
        assertThat(chat.statusCode()).isEqualTo(200);

        assertThat(send("GET", applicationPort, "/actuator/health", null).statusCode())
                .isEqualTo(404);

        HttpResponse<String> health = send("GET", managementPort, "/actuator/health", null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");

        HttpResponse<String> metrics = send(
                "GET",
                managementPort,
                "/actuator/metrics/ticketops.agent.request",
                null
        );
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("ticketops.agent.request")
                .contains("outcome")
                .contains("category")
                .contains("risk");

        assertThat(send("GET", managementPort, "/actuator/env", null).statusCode())
                .isEqualTo(404);
    }

    private HttpResponse<String> send(
            String method,
            int port,
            String path,
            String body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
