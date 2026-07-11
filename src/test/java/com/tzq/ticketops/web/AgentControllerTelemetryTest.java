package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentExecutionLog;
import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.AgentOrchestrator;
import com.tzq.ticketops.observability.MicrometerAgentTelemetry;
import com.tzq.ticketops.ticket.TicketService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentControllerTelemetryTest {

    @Test
    void recordsErrorInsteadOfCompletedWhenAuditPersistenceFails() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentController controller = new AgentController(
                AgentOrchestrator.createDefault(),
                new TicketService(),
                new FailingLogRepository(),
                new MicrometerAgentTelemetry(registry)
        );

        assertThatThrownBy(() -> controller.chat(new ChatRequest(
                "mock-user-001",
                "OA login failed",
                "The account is locked."
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("audit persistence failed");

        assertThat(registry.get("ticketops.agent.request")
                .tags("outcome", "error", "category", "unknown", "risk", "unknown")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.find("ticketops.agent.request")
                .tags("outcome", "completed")
                .timer()).isNull();
    }

    private static final class FailingLogRepository implements AgentExecutionLogRepository {

        @Override
        public void save(AgentExecutionLog log) {
            throw new IllegalStateException("audit persistence failed");
        }

        @Override
        public Optional<AgentExecutionLog> findByTicketId(String ticketId) {
            return Optional.empty();
        }
    }
}
