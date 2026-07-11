package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDraftPersistenceArtifactsTest {

    @Test
    void guideDocumentsStorageApiAtomicityAndDraftOnlyBoundary() throws Exception {
        Path guide = Path.of("docs", "api", "agent-draft-persistence.md");

        assertThat(guide).exists();
        assertThat(Files.readString(guide))
                .contains("# Agent Draft Persistence")
                .contains("ticket_message")
                .contains("INTERNAL_SUGGESTION")
                .contains("USER_REPLY_DRAFT")
                .contains("/api/tickets/{ticketId}/messages")
                .contains("transaction")
                .contains("draft-only")
                .contains("No send endpoint");
    }

    @Test
    void readmeExposesPersistedDraftCapabilityAndBoundary() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## Persisted Agent Drafts")
                .contains("/api/tickets/{ticketId}/messages")
                .contains("INTERNAL_SUGGESTION")
                .contains("USER_REPLY_DRAFT")
                .contains("No message is automatically sent");
    }
}
