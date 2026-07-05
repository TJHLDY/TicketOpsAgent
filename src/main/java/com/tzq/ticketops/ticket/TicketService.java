package com.tzq.ticketops.ticket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TicketService {

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();
    private final JdbcTemplate jdbcTemplate;

    public TicketService() {
        this.jdbcTemplate = null;
    }

    @Autowired
    public TicketService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public synchronized Ticket createTicket(String requesterId, String title, String description) {
        Ticket ticket = new Ticket(
                nextTicketId(),
                requesterId,
                title,
                description,
                TicketStatus.OPEN,
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );
        if (usesJdbc()) {
            jdbcTemplate.update(
                    """
                            insert into ticket(id, requester_id, title, description, status, created_at)
                            values (?, ?, ?, ?, ?, ?)
                            """,
                    ticket.id(),
                    ticket.requesterId(),
                    ticket.title(),
                    ticket.description(),
                    ticket.status().name(),
                    Timestamp.from(ticket.createdAt())
            );
        } else {
            tickets.put(ticket.id(), ticket);
        }
        return ticket;
    }

    public synchronized void clear() {
        if (usesJdbc()) {
            jdbcTemplate.update("delete from pending_action");
            jdbcTemplate.update("delete from tool_call_log");
            jdbcTemplate.update("delete from agent_trace");
            jdbcTemplate.update("delete from ticket_message");
            jdbcTemplate.update("delete from ticket");
        } else {
            tickets.clear();
        }
        sequence.set(1);
    }

    public synchronized Optional<Ticket> findById(String id) {
        if (usesJdbc()) {
            return jdbcTemplate.query(
                    """
                            select id, requester_id, title, description, status, created_at
                            from ticket
                            where id = ?
                            """,
                    (rs, rowNum) -> new Ticket(
                            rs.getString("id"),
                            rs.getString("requester_id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            TicketStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant()
                    ),
                    id
            ).stream().findFirst();
        }
        return Optional.ofNullable(tickets.get(id));
    }

    public synchronized List<Ticket> findAll() {
        if (usesJdbc()) {
            return jdbcTemplate.query(
                    """
                            select id, requester_id, title, description, status, created_at
                            from ticket
                            order by created_at, id
                            """,
                    (rs, rowNum) -> new Ticket(
                            rs.getString("id"),
                            rs.getString("requester_id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            TicketStatus.valueOf(rs.getString("status")),
                            rs.getTimestamp("created_at").toInstant()
                    )
            );
        }
        return List.copyOf(tickets.values());
    }

    private boolean usesJdbc() {
        return jdbcTemplate != null;
    }

    private String nextTicketId() {
        if (usesJdbc()) {
            return "TICKET-" + UUID.randomUUID();
        }
        return "TICKET-" + sequence.getAndIncrement();
    }
}
