package com.tzq.ticketops.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioPlaybookArtifactsTest {

    @Test
    void readmeDocumentsScenarioAcceptanceEntryPoint() throws Exception {
        String readme = Files.readString(Path.of("README.md"));

        assertThat(readme)
                .contains("## Scenario Acceptance")
                .contains("mvn test \"-Dtest=ScenarioAcceptanceTest\"")
                .contains("docs/scenarios/scenario-playbook.md")
                .contains("account lock, permission request, MFA issue, prompt injection rejection, and non-IT request rejection");
    }

    @Test
    void scenarioPlaybookDocumentsCoreTicketFlowsAndBoundaries() throws Exception {
        String playbook = Files.readString(Path.of("docs/scenarios/scenario-playbook.md"));

        assertThat(playbook)
                .contains("# Scenario Acceptance Playbook")
                .contains("OA account locked")
                .contains("CRM permission request")
                .contains("VPN MFA issue")
                .contains("Prompt injection rejection")
                .contains("Non-IT request rejection")
                .contains("ACCOUNT_LOCKED")
                .contains("PERMISSION_REQUEST")
                .contains("MFA_ISSUE")
                .contains("UNKNOWN")
                .contains("getAccountStatus")
                .contains("getUserPermissions")
                .contains("UNLOCK_ACCOUNT")
                .contains("GRANT_PERMISSION")
                .contains("NOT_EXECUTED_MOCK_ONLY")
                .contains("no real unlock, password reset, permission grant, dispatch, or close-ticket operation")
                .contains("mvn test \"-Dtest=ScenarioAcceptanceTest\"");
    }
}
