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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PendingActionReviewStateMachineTest {

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
    void approvedActionCannotBeApprovedAgain() throws Exception {
        long actionId = insertPendingAction("PENDING");

        approve(actionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED_MOCK_ONLY"));

        approve(actionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ACTION_ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.message").value("Pending action already reviewed"));
    }

    @Test
    void approvedActionCannotBeRejected() throws Exception {
        long actionId = insertPendingAction("PENDING");

        approve(actionId).andExpect(status().isOk());

        reject(actionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ACTION_ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.message").value("Pending action already reviewed"));
    }

    @Test
    void cancelledActionCannotBeApproved() throws Exception {
        long actionId = insertPendingAction("CANCELLED");

        approve(actionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PENDING_ACTION_ALREADY_REVIEWED"))
                .andExpect(jsonPath("$.message").value("Pending action already reviewed"));
    }

    @Test
    void blankReviewerIdIsInvalid() throws Exception {
        long actionId = insertPendingAction("PENDING");

        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"","reviewComment":"Approved for audit demo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void overlongReviewCommentIsInvalid() throws Exception {
        long actionId = insertPendingAction("PENDING");

        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"%s"}
                                """.formatted("x".repeat(201))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @Test
    void successfulReviewNeverExecutesRealOperation() throws Exception {
        long actionId = insertPendingAction("PENDING");

        approve(actionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED_MOCK_ONLY"));
    }

    private org.springframework.test.web.servlet.ResultActions approve(long actionId) throws Exception {
        return mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reviewerId":"admin-mock","reviewComment":"Approved for audit demo"}
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions reject(long actionId) throws Exception {
        return mockMvc.perform(post("/api/pending-actions/{actionId}/reject", actionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"reviewerId":"admin-mock","reviewComment":"Rejected for audit demo"}
                        """));
    }

    private long insertPendingAction(String status) {
        String ticketId = "ticket-state-" + status.toLowerCase();
        jdbcTemplate.update(
                """
                        insert into ticket(id, requester_id, title, description, status, category, priority, risk_level)
                        values (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                ticketId,
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
                ticketId,
                0,
                "UNLOCK_ACCOUNT",
                "Wait for approval",
                status
        );
        return jdbcTemplate.queryForObject(
                "select id from pending_action where ticket_id = ?",
                Long.class,
                ticketId
        );
    }
}
