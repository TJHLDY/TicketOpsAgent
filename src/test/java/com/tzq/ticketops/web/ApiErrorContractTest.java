package com.tzq.ticketops.web;

import com.tzq.ticketops.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorContractTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    TicketService ticketService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        ticketService.clear();
    }

    @Test
    void missingTicketReturnsStableErrorContract() throws Exception {
        mockMvc.perform(get("/api/tickets/{ticketId}", "missing-ticket"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TICKET_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ticket not found"))
                .andExpect(jsonPath("$.path").value("/api/tickets/missing-ticket"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingPendingActionReturnsStableErrorContract() throws Exception {
        mockMvc.perform(get("/api/pending-actions/{actionId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Pending action not found"))
                .andExpect(jsonPath("$.path").value("/api/pending-actions/999999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void approvingMissingPendingActionReturnsStableErrorContract() throws Exception {
        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"Missing action"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ACTION_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Pending action not found"))
                .andExpect(jsonPath("$.path").value("/api/pending-actions/999999/approve"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidReviewRequestReturnsStableErrorContract() throws Exception {
        long actionId = insertPendingAction("PENDING");

        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"","reviewComment":"Approved for demo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"))
                .andExpect(jsonPath("$.path").value("/api/pending-actions/" + actionId + "/approve"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    private long insertPendingAction(String status) {
        jdbcTemplate.update(
                """
                        insert into ticket(id, requester_id, title, description, status, category, priority, risk_level)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                "ticket-api-error",
                "mock-user-001",
                "OA login failed",
                "Account locked",
                "OPEN",
                "ACCOUNT_LOCKED",
                "P2",
                "NEEDS_APPROVAL"
        );
        jdbcTemplate.update(
                """
                        insert into pending_action(ticket_id, action_order, action_type, summary, status)
                        values (?, ?, ?, ?, ?)
                        """,
                "ticket-api-error",
                0,
                "UNLOCK_ACCOUNT",
                "Wait for approval",
                status
        );
        return jdbcTemplate.queryForObject(
                "select id from pending_action where ticket_id = ?",
                Long.class,
                "ticket-api-error"
        );
    }
}
