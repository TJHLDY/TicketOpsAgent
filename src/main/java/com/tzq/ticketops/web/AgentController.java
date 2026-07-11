package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentOrchestrator;
import com.tzq.ticketops.agent.AgentExecutionLog;
import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.AgentRequest;
import com.tzq.ticketops.agent.AgentResponse;
import com.tzq.ticketops.observability.AgentRequestOutcome;
import com.tzq.ticketops.observability.AgentTelemetry;
import com.tzq.ticketops.ticket.Ticket;
import com.tzq.ticketops.ticket.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator agentOrchestrator;
    private final TicketService ticketService;
    private final AgentExecutionLogRepository logRepository;
    private final AgentTelemetry telemetry;

    public AgentController(
            AgentOrchestrator agentOrchestrator,
            TicketService ticketService,
            AgentExecutionLogRepository logRepository,
            AgentTelemetry telemetry
    ) {
        this.agentOrchestrator = agentOrchestrator;
        this.ticketService = ticketService;
        this.logRepository = logRepository;
        this.telemetry = telemetry;
    }

    @PostMapping(
            value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
    )
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        AgentTelemetry.RequestObservation observation = telemetry.startRequest();
        try {
            Ticket ticket = ticketService.createTicket(request.requesterId(), request.title(), request.description());
            AgentResponse response = agentOrchestrator.handle(new AgentRequest(
                    request.requesterId(),
                    request.title(),
                    request.description()
            ));
            ticketService.updateDecisionSummary(
                    ticket.id(),
                    response.category(),
                    response.priority(),
                    response.riskLevel()
            );
            logRepository.save(new AgentExecutionLog(
                    ticket.id(),
                    response.traceEvents(),
                    response.toolCalls(),
                    response.pendingActions()
            ));
            ChatResponse chatResponse = ChatResponse.from(ticket.id(), response);
            observation.complete(
                    AgentRequestOutcome.from(response),
                    response.category(),
                    response.riskLevel()
            );
            return chatResponse;
        } catch (RuntimeException exception) {
            observation.error();
            throw exception;
        }
    }
}
