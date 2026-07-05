package com.tzq.ticketops.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TicketServicePersistenceTest {

    @Autowired
    TicketService ticketService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTickets() {
        ticketService.clear();
    }

    @Test
    void createTicketPersistsOpenTicketRow() {
        Ticket ticket = ticketService.createTicket(
                "mock-user-001",
                "OA 登录失败",
                "我登录 OA 系统失败，提示账号已锁定。"
        );

        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from ticket where id = ? and requester_id = ? and status = ?",
                Integer.class,
                ticket.id(),
                "mock-user-001",
                "OPEN"
        );
        assertThat(rows).isEqualTo(1);
        Timestamp createdAt = jdbcTemplate.queryForObject(
                "select created_at from ticket where id = ?",
                Timestamp.class,
                ticket.id()
        );
        assertThat(createdAt.toInstant()).isEqualTo(ticket.createdAt());
        assertThat(ticketService.findById(ticket.id())).contains(ticket);
    }

    @Test
    void jdbcTicketIdsDoNotRestartAtExistingRows() {
        jdbcTemplate.update(
                """
                        insert into ticket(id, requester_id, title, description, status, created_at)
                        values (?, ?, ?, ?, ?, ?)
                        """,
                "TICKET-1",
                "mock-user-001",
                "existing",
                "existing ticket from a previous app run",
                "OPEN",
                Timestamp.from(java.time.Instant.now())
        );

        TicketService freshService = new TicketService(jdbcTemplate);
        Ticket ticket = freshService.createTicket(
                "mock-user-001",
                "new",
                "new ticket after restart"
        );

        assertThat(ticket.id()).isNotEqualTo("TICKET-1");
        Integer rows = jdbcTemplate.queryForObject("select count(*) from ticket", Integer.class);
        assertThat(rows).isEqualTo(2);
    }
}
