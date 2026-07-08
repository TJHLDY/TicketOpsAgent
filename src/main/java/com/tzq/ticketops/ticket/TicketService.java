package com.tzq.ticketops.ticket;

import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Ticket ticket = new Ticket(
                nextTicketId(),
                requesterId,
                title,
                description,
                TicketStatus.OPEN,
                null,
                null,
                null,
                now,
                now
        );
        if (usesJdbc()) {
            jdbcTemplate.update(
                    """
                            insert into ticket(id, requester_id, title, description, status, updated_at, created_at)
                            values (?, ?, ?, ?, ?, ?, ?)
                            """,
                    ticket.id(),
                    ticket.requesterId(),
                    ticket.title(),
                    ticket.description(),
                    ticket.status().name(),
                    Timestamp.from(ticket.updatedAt()),
                    Timestamp.from(ticket.createdAt())
            );
        } else {
            tickets.put(ticket.id(), ticket);
        }
        return ticket;
    }

    public synchronized void updateDecisionSummary(
            String ticketId,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel
    ) {
        Instant updatedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (usesJdbc()) {
            jdbcTemplate.update(
                    """
                            update ticket
                            set category = ?, priority = ?, risk_level = ?, updated_at = ?
                            where id = ?
                            """,
                    category.name(),
                    priority.name(),
                    riskLevel.name(),
                    Timestamp.from(updatedAt),
                    ticketId
            );
            return;
        }
        Ticket existing = tickets.get(ticketId);
        if (existing != null) {
            tickets.put(ticketId, new Ticket(
                    existing.id(),
                    existing.requesterId(),
                    existing.title(),
                    existing.description(),
                    existing.status(),
                    category,
                    priority,
                    riskLevel,
                    updatedAt,
                    existing.createdAt()
            ));
        }
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
                            select id, requester_id, title, description, status, category, priority, risk_level, updated_at, created_at
                            from ticket
                            where id = ?
                            """,
                    (rs, rowNum) -> mapTicket(rs),
                    id
            ).stream().findFirst();
        }
        return Optional.ofNullable(tickets.get(id));
    }

    public synchronized List<Ticket> findAll() {
        if (usesJdbc()) {
            return jdbcTemplate.query(
                    """
                            select id, requester_id, title, description, status, category, priority, risk_level, updated_at, created_at
                            from ticket
                            order by created_at, id
                            """,
                    (rs, rowNum) -> mapTicket(rs)
            );
        }
        return List.copyOf(tickets.values());
    }

    public synchronized List<Ticket> search(
            TicketStatus status,
            TicketCategory category,
            String requesterId,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        if (!usesJdbc()) {
            return tickets.values().stream()
                    .filter(ticket -> status == null || ticket.status() == status)
                    .filter(ticket -> category == null || ticket.category() == category)
                    .filter(ticket -> requesterId == null || requesterId.isBlank() || ticket.requesterId().equals(requesterId))
                    .skip((long) safePage * safeSize)
                    .limit(safeSize)
                    .toList();
        }

        StringBuilder sql = new StringBuilder("""
                select id, requester_id, title, description, status, category, priority, risk_level, updated_at, created_at
                from ticket
                where 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" and status = ?");
            args.add(status.name());
        }
        if (category != null) {
            sql.append(" and category = ?");
            args.add(category.name());
        }
        if (requesterId != null && !requesterId.isBlank()) {
            sql.append(" and requester_id = ?");
            args.add(requesterId);
        }
        sql.append(" order by created_at desc, id desc limit ? offset ?");
        args.add(safeSize);
        args.add(safePage * safeSize);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapTicket(rs), args.toArray());
    }

    private Ticket mapTicket(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Ticket(
                rs.getString("id"),
                rs.getString("requester_id"),
                rs.getString("title"),
                rs.getString("description"),
                TicketStatus.valueOf(rs.getString("status")),
                nullableEnum(TicketCategory.class, rs.getString("category")),
                nullableEnum(TicketPriority.class, rs.getString("priority")),
                nullableEnum(RiskLevel.class, rs.getString("risk_level")),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private <T extends Enum<T>> T nullableEnum(Class<T> enumType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(enumType, value);
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
