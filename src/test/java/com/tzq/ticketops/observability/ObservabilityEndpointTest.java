package com.tzq.ticketops.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityEndpointTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void exposesHealthAndAgentMetricsButNotEnvironment() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requesterId": "mock-user-001",
                                  "title": "OA login failed",
                                  "description": "The account is locked."
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/metrics/ticketops.agent.request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("ticketops.agent.request"))
                .andExpect(jsonPath("$.measurements[0].value").isNumber())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'outcome')]").exists())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'category')]").exists())
                .andExpect(jsonPath("$.availableTags[?(@.tag == 'risk')]").exists());

        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
