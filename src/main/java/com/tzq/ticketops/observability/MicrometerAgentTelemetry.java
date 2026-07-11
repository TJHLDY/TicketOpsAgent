package com.tzq.ticketops.observability;

import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.rag.SopSearchStatus;
import com.tzq.ticketops.tools.ReadOnlyToolExecutor;
import com.tzq.ticketops.tools.ToolRejectionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public final class MicrometerAgentTelemetry implements AgentTelemetry {

    private static final String AGENT_REQUEST = "ticketops.agent.request";
    private static final String RAG_RETRIEVAL = "ticketops.rag.retrieval";
    private static final String TOOL_EXECUTION = "ticketops.tool.execution";
    private static final String PENDING_ACTION = "ticketops.pending.action";
    private static final String SHADOW_DECISION = "ticketops.shadow.decision";

    private static final Set<String> KNOWN_PROVIDERS = Set.of("offline", "onnx");
    private static final Set<String> KNOWN_TOOLS = Set.of(
            ReadOnlyToolExecutor.GET_ACCOUNT_STATUS,
            ReadOnlyToolExecutor.GET_USER_PERMISSIONS
    );

    private final MeterRegistry registry;

    public MicrometerAgentTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public RequestObservation startRequest() {
        Timer.Sample sample = Timer.start(registry);
        AtomicBoolean stopped = new AtomicBoolean();
        return new RequestObservation() {
            @Override
            public void complete(
                    AgentRequestOutcome outcome,
                    TicketCategory category,
                    RiskLevel riskLevel
            ) {
                if (!stopped.compareAndSet(false, true)) {
                    return;
                }
                sample.stop(Timer.builder(AGENT_REQUEST)
                        .description("TicketOpsAgent request processing time")
                        .tag("outcome", enumTag(outcome))
                        .tag("category", enumTag(category))
                        .tag("risk", enumTag(riskLevel))
                        .register(registry));
            }

            @Override
            public void error() {
                complete(AgentRequestOutcome.ERROR, null, null);
            }
        };
    }

    @Override
    public void recordRag(SopSearchStatus status, String provider) {
        counter(
                RAG_RETRIEVAL,
                "SOP retrieval outcomes",
                "status", enumTag(status),
                "provider", normalizedProvider(provider)
        ).increment();
    }

    @Override
    public void recordToolSuccess(String toolName) {
        counter(
                TOOL_EXECUTION,
                "Controlled read-only tool execution outcomes",
                "tool", normalizedTool(toolName),
                "outcome", "success",
                "reason", "none"
        ).increment();
    }

    @Override
    public void recordToolRejected(String toolName, ToolRejectionReason reason) {
        counter(
                TOOL_EXECUTION,
                "Controlled read-only tool execution outcomes",
                "tool", normalizedTool(toolName),
                "outcome", "rejected",
                "reason", enumTag(reason)
        ).increment();
    }

    @Override
    public void recordPendingAction(PendingActionType type) {
        counter(
                PENDING_ACTION,
                "Audit-only pending action proposals",
                "type", enumTag(type)
        ).increment();
    }

    @Override
    public void recordShadow(ShadowDecisionOutcome outcome) {
        counter(
                SHADOW_DECISION,
                "Shadow decision outcomes",
                "outcome", enumTag(outcome)
        ).increment();
    }

    private Counter counter(String name, String description, String... tags) {
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    private String normalizedProvider(String provider) {
        if (provider == null) {
            return "other";
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return KNOWN_PROVIDERS.contains(normalized) ? normalized : "other";
    }

    private String normalizedTool(String toolName) {
        return KNOWN_TOOLS.contains(toolName) ? toolName : "other";
    }

    private String enumTag(Enum<?> value) {
        return value == null ? "unknown" : value.name().toLowerCase(Locale.ROOT);
    }
}
