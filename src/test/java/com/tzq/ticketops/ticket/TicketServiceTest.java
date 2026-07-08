package com.tzq.ticketops.ticket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketServiceTest {

    @Test
    void createsOpenTicketFromSubmittedTitleAndDescription() {
        TicketService ticketService = new TicketService();

        Ticket ticket = ticketService.createTicket(
                "mock-user-001",
                "OA 登录失败",
                "我登录 OA 系统失败，提示账号已锁定，帮我恢复一下。"
        );

        assertThat(ticket.id()).isNotBlank();
        assertThat(ticket.requesterId()).isEqualTo("mock-user-001");
        assertThat(ticket.title()).isEqualTo("OA 登录失败");
        assertThat(ticket.description()).contains("账号已锁定");
        assertThat(ticket.status()).isEqualTo(TicketStatus.OPEN);
    }

    @Test
    void storesCreatedTicketForLaterLookup() {
        TicketService ticketService = new TicketService();

        Ticket ticket = ticketService.createTicket(
                "mock-user-001",
                "OA 登录失败",
                "账号已锁定。"
        );

        assertThat(ticketService.findById(ticket.id())).contains(ticket);
        assertThat(ticketService.findAll()).containsExactly(ticket);
    }
}
