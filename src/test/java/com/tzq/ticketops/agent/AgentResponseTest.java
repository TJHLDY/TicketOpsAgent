package com.tzq.ticketops.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResponseTest {

    @Test
    void boundsGeneratedDraftsToThePersistenceContract() {
        AgentResponse response = new AgentResponse(
                TicketCategory.UNKNOWN,
                TicketPriority.P3,
                RiskLevel.READ_ONLY,
                List.of(),
                List.of(),
                "s".repeat(AgentResponse.MAX_DRAFT_LENGTH + 1),
                "r".repeat(AgentResponse.MAX_DRAFT_LENGTH + 500),
                List.of(),
                List.of()
        );

        assertThat(response.suggestion()).hasSize(AgentResponse.MAX_DRAFT_LENGTH);
        assertThat(response.replyDraft()).hasSize(AgentResponse.MAX_DRAFT_LENGTH);
    }

    @Test
    void doesNotSplitSupplementaryUnicodeCharacterAtDraftBoundary() {
        String prefix = "a".repeat(AgentResponse.MAX_DRAFT_LENGTH - 1);
        AgentResponse response = new AgentResponse(
                TicketCategory.UNKNOWN,
                TicketPriority.P3,
                RiskLevel.READ_ONLY,
                List.of(),
                List.of(),
                prefix + "\uD83D\uDE80" + "tail",
                "reply",
                List.of(),
                List.of()
        );

        assertThat(response.suggestion()).isEqualTo(prefix);
        assertThat(response.suggestion().chars().anyMatch(codeUnit -> Character.isSurrogate((char) codeUnit))).isFalse();
    }
}
