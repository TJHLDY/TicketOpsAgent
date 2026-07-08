package com.tzq.ticketops.web;

import com.tzq.ticketops.agent.AgentExecutionLogRepository;
import com.tzq.ticketops.agent.PendingActionRecord;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import com.tzq.ticketops.agent.ToolCallLogRecord;
import com.tzq.ticketops.agent.TraceEventRecord;
import com.tzq.ticketops.ticket.Ticket;
import com.tzq.ticketops.ticket.TicketService;
import com.tzq.ticketops.ticket.TicketStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketQueryController {

    private final TicketService ticketService;
    private final AgentExecutionLogRepository logRepository;

    public TicketQueryController(TicketService ticketService, AgentExecutionLogRepository logRepository) {
        this.ticketService = ticketService;
        this.logRepository = logRepository;
    }

    @GetMapping("/{ticketId}")
    public TicketDto findTicket(@PathVariable String ticketId) {
        return ticketService.findById(ticketId)
                .map(TicketDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket not found"));
    }

    @GetMapping
    public TicketListDto listTickets(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketCategory category,
            @RequestParam(required = false) String requesterId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<TicketDto> items = ticketService.search(status, category, requesterId, page, size)
                .stream()
                .map(TicketDto::from)
                .toList();
        return new TicketListDto(items, Math.max(page, 0), Math.max(1, Math.min(size, 100)));
    }

    @GetMapping("/{ticketId}/trace")
    public List<TraceEventDto> trace(@PathVariable String ticketId) {
        ensureTicketExists(ticketId);
        return logRepository.findTraceEventsByTicketId(ticketId)
                .stream()
                .map(TraceEventDto::from)
                .toList();
    }

    @GetMapping("/{ticketId}/tool-calls")
    public List<ToolCallDto> toolCalls(@PathVariable String ticketId) {
        ensureTicketExists(ticketId);
        return logRepository.findToolCallsByTicketId(ticketId)
                .stream()
                .map(ToolCallDto::from)
                .toList();
    }

    @GetMapping("/{ticketId}/pending-actions")
    public List<PendingActionDto> pendingActions(@PathVariable String ticketId) {
        ensureTicketExists(ticketId);
        return logRepository.findPendingActionsByTicketId(ticketId)
                .stream()
                .map(PendingActionDto::from)
                .toList();
    }

    private void ensureTicketExists(String ticketId) {
        if (ticketService.findById(ticketId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ticket not found");
        }
    }

    public record TicketDto(
            String id,
            String requesterId,
            String title,
            String description,
            TicketStatus status,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel,
            Instant createdAt,
            Instant updatedAt
    ) {
        static TicketDto from(Ticket ticket) {
            return new TicketDto(
                    ticket.id(),
                    ticket.requesterId(),
                    ticket.title(),
                    ticket.description(),
                    ticket.status(),
                    ticket.category(),
                    ticket.priority(),
                    ticket.riskLevel(),
                    ticket.createdAt(),
                    ticket.updatedAt()
            );
        }
    }

    public record TicketListDto(List<TicketDto> items, int page, int size) {
    }

    public record TraceEventDto(
            long id,
            String ticketId,
            int stepOrder,
            String step,
            String detail,
            Instant createdAt
    ) {
        static TraceEventDto from(TraceEventRecord record) {
            return new TraceEventDto(
                    record.id(),
                    record.ticketId(),
                    record.stepOrder(),
                    record.step(),
                    record.detail(),
                    record.createdAt()
            );
        }
    }

    public record ToolCallDto(
            long id,
            String ticketId,
            int toolOrder,
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            Instant createdAt
    ) {
        static ToolCallDto from(ToolCallLogRecord record) {
            return new ToolCallDto(
                    record.id(),
                    record.ticketId(),
                    record.toolOrder(),
                    record.toolName(),
                    record.arguments(),
                    record.resultSummary(),
                    record.createdAt()
            );
        }
    }

    public record PendingActionDto(
            long id,
            String ticketId,
            int actionOrder,
            String actionType,
            String summary,
            String status,
            String reviewerId,
            String reviewComment,
            Instant reviewedAt,
            String executionStatus,
            Instant createdAt
    ) {
        static PendingActionDto from(PendingActionRecord record) {
            return new PendingActionDto(
                    record.id(),
                    record.ticketId(),
                    record.actionOrder(),
                    record.actionType().name(),
                    record.summary(),
                    record.status().name(),
                    record.reviewerId(),
                    record.reviewComment(),
                    record.reviewedAt(),
                    record.executionStatus(),
                    record.createdAt()
            );
        }
    }
}
