package com.tzq.ticketops.tools;

import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.decision.ToolIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReadOnlyToolExecutorTest {

    private MockAccountStatusTool accountStatusTool;
    private MockUserPermissionsTool permissionsTool;
    private ReadOnlyToolExecutor executor;

    @BeforeEach
    void setUp() {
        accountStatusTool = mock(MockAccountStatusTool.class);
        permissionsTool = mock(MockUserPermissionsTool.class);
        executor = new ReadOnlyToolExecutor(accountStatusTool, permissionsTool, 1);
    }

    @Test
    void executesValidatedAccountStatusIntentExactlyOnce() {
        when(accountStatusTool.getAccountStatus("mock-user-001"))
                .thenReturn(new AccountStatusResult("mock-user-001", AccountStatus.LOCKED));

        ToolExecutionResult result = executor.executeSingle(
                List.of(new ToolIntent("getAccountStatus", Map.of("userId", "mock-user-001"))),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED
        );

        assertThat(result.toolCall().toolName()).isEqualTo("getAccountStatus");
        assertThat(result.toolCall().arguments()).containsExactlyEntriesOf(Map.of("userId", "mock-user-001"));
        assertThat(result.toolCall().resultSummary()).isEqualTo("LOCKED");
        assertThat(result.budgetUsed()).isEqualTo(1);
        assertThat(result.budgetLimit()).isEqualTo(1);
        assertThat(result.emptyResult()).isFalse();
        verify(accountStatusTool).getAccountStatus("mock-user-001");
        verifyNoInteractions(permissionsTool);
    }

    @Test
    void normalizesPermissionAppCodeBeforeInvocation() {
        when(permissionsTool.getUserPermissions("mock-user-005", "CRM"))
                .thenReturn(new UserPermissionsResult("mock-user-005", "CRM", List.of("CRM_VIEW")));

        ToolExecutionResult result = executor.executeSingle(
                List.of(new ToolIntent(
                        "getUserPermissions",
                        Map.of("userId", "mock-user-005", "appCode", " crm ")
                )),
                "mock-user-005",
                TicketCategory.PERMISSION_REQUEST
        );

        assertThat(result.toolCall().arguments()).containsEntry("appCode", "CRM");
        assertThat(result.toolCall().resultSummary()).isEqualTo("CRM_VIEW");
        assertThat(result.emptyResult()).isFalse();
        verify(permissionsTool).getUserPermissions("mock-user-005", "CRM");
        verifyNoInteractions(accountStatusTool);
    }

    @Test
    void rejectsUnknownToolBeforeInvocation() {
        assertRejected(
                List.of(new ToolIntent("unlockAccountNow", Map.of("userId", "mock-user-001"))),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.UNAUTHORIZED_TOOL
        );
    }

    @Test
    void rejectsToolThatDoesNotMatchCategory() {
        assertRejected(
                List.of(new ToolIntent(
                        "getUserPermissions",
                        Map.of("userId", "mock-user-001", "appCode", "OA")
                )),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.CATEGORY_TOOL_MISMATCH
        );
    }

    @Test
    void rejectsRequesterIdentityMismatch() {
        assertRejected(
                List.of(new ToolIntent("getAccountStatus", Map.of("userId", "mock-user-002"))),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.REQUESTER_MISMATCH
        );
    }

    @Test
    void rejectsMissingAndExtraArguments() {
        assertRejected(
                List.of(new ToolIntent("getAccountStatus", Map.of())),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.INVALID_ARGUMENTS
        );
        Map<String, String> nullValueArguments = new HashMap<>();
        nullValueArguments.put("userId", null);
        assertRejected(
                List.of(new ToolIntent("getAccountStatus", nullValueArguments)),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.INVALID_ARGUMENTS
        );
        assertRejected(
                List.of(new ToolIntent(
                        "getAccountStatus",
                        Map.of("userId", "mock-user-001", "force", "true")
                )),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.INVALID_ARGUMENTS
        );
    }

    @Test
    void rejectsUnsupportedApplicationCode() {
        assertRejected(
                List.of(new ToolIntent(
                        "getUserPermissions",
                        Map.of("userId", "mock-user-005", "appCode", "ROOT")
                )),
                "mock-user-005",
                TicketCategory.PERMISSION_REQUEST,
                ToolRejectionReason.UNSUPPORTED_APP_CODE
        );
    }

    @Test
    void rejectsMissingIntentAndBudgetOverflowBeforeInvocation() {
        assertThatThrownBy(() -> new ReadOnlyToolExecutor(accountStatusTool, permissionsTool, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal 1");
        assertRejected(
                List.of(),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.MISSING_TOOL_INTENT
        );
        assertRejected(
                java.util.Collections.singletonList(null),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.UNAUTHORIZED_TOOL
        );
        assertRejected(
                List.of(
                        new ToolIntent("getAccountStatus", Map.of("userId", "mock-user-001")),
                        new ToolIntent("getAccountStatus", Map.of("userId", "mock-user-001"))
                ),
                "mock-user-001",
                TicketCategory.ACCOUNT_LOCKED,
                ToolRejectionReason.TOOL_BUDGET_EXCEEDED
        );
    }

    private void assertRejected(
            List<ToolIntent> intents,
            String requesterId,
            TicketCategory category,
            ToolRejectionReason reason
    ) {
        assertThatThrownBy(() -> executor.executeSingle(intents, requesterId, category))
                .isInstanceOfSatisfying(
                        ToolExecutionRejectedException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason)
                );
        verifyNoInteractions(accountStatusTool, permissionsTool);
    }
}
