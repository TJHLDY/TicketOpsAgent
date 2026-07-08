package com.tzq.ticketops.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzq.ticketops.ticket.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApiProductizationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TicketService ticketService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        ticketService.clear();
        jdbcTemplate.update("delete from mock_user_permission");
        jdbcTemplate.update("delete from mock_user_account");
        jdbcTemplate.update(
                "insert into mock_user_account(user_id, account_status, display_name) values (?, ?, ?)",
                "mock-user-001",
                "LOCKED",
                "mock user"
        );
    }

    @Test
    void ticketDetailAndListExposeDecisionSummary() throws Exception {
        String accountTicketId = createAccountLockedTicket();
        createUnknownTicket();

        mockMvc.perform(get("/api/tickets/{ticketId}", accountTicketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountTicketId))
                .andExpect(jsonPath("$.requesterId").value("mock-user-001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.category").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.priority").value("P2"))
                .andExpect(jsonPath("$.riskLevel").value("NEEDS_APPROVAL"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        mockMvc.perform(get("/api/tickets/{ticketId}", "missing-ticket"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tickets")
                        .param("status", "OPEN")
                        .param("category", "ACCOUNT_LOCKED")
                        .param("requesterId", "mock-user-001")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(accountTicketId))
                .andExpect(jsonPath("$.items[0].category").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void traceToolCallAndPendingActionApisExposeAuditTimeline() throws Exception {
        String ticketId = createAccountLockedTicket();

        mockMvc.perform(get("/api/tickets/{ticketId}/trace", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].step").value("CLASSIFY"))
                .andExpect(jsonPath("$[0].detail").value("category=ACCOUNT_LOCKED, priority=P2, risk=NEEDS_APPROVAL"))
                .andExpect(jsonPath("$[0].createdAt").exists());

        mockMvc.perform(get("/api/tickets/{ticketId}/tool-calls", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].toolName").value("getAccountStatus"))
                .andExpect(jsonPath("$[0].arguments.userId").value("mock-user-001"))
                .andExpect(jsonPath("$[0].resultSummary").value("LOCKED"))
                .andExpect(jsonPath("$[0].createdAt").exists());

        mockMvc.perform(get("/api/tickets/{ticketId}/pending-actions", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].ticketId").value(ticketId))
                .andExpect(jsonPath("$[0].actionType").value("UNLOCK_ACCOUNT"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].executionStatus").value("NOT_EXECUTED_MOCK_ONLY"));
    }

    @Test
    void approvePendingActionDoesNotExecuteRealOperation() throws Exception {
        String ticketId = createAccountLockedTicket();
        long actionId = firstPendingActionId(ticketId);

        mockMvc.perform(get("/api/pending-actions/{actionId}", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actionId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"Approved for audit demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actionId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewerId").value("admin-mock"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED_MOCK_ONLY"))
                .andExpect(jsonPath("$.message").value("Action approved for audit demo only. No real account operation was executed."));

        String accountStatus = jdbcTemplate.queryForObject(
                "select account_status from mock_user_account where user_id = ?",
                String.class,
                "mock-user-001"
        );
        assertThat(accountStatus).isEqualTo("LOCKED");

        mockMvc.perform(post("/api/pending-actions/{actionId}/approve", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"Repeat approve"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/pending-actions/{actionId}/reject", 999999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"Missing action"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void pendingActionCanBeRejected() throws Exception {
        String ticketId = createAccountLockedTicket();
        long actionId = firstPendingActionId(ticketId);

        mockMvc.perform(post("/api/pending-actions/{actionId}/reject", actionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewerId":"admin-mock","reviewComment":"Rejected for audit demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(actionId))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.executionStatus").value("NOT_EXECUTED_MOCK_ONLY"))
                .andExpect(jsonPath("$.message").value("Action rejected for audit demo only. No real account operation was executed."));
    }

    private String createAccountLockedTicket() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(
                                "mock-user-001",
                                "OA login failed",
                                "锁定"
                        ))))
                .andExpect(status().isOk());
        return ticketService.findAll().get(ticketService.findAll().size() - 1).id();
    }

    private void createUnknownTicket() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest(
                                "mock-user-002",
                                "cafeteria",
                                "question about cafeteria menu"
                        ))))
                .andExpect(status().isOk());
    }

    private long firstPendingActionId(String ticketId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tickets/{ticketId}/pending-actions", ticketId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root).hasSize(1);
        return root.get(0).get("id").asLong();
    }
}
