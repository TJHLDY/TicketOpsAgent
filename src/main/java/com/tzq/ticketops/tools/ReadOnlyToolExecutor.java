package com.tzq.ticketops.tools;

import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.ToolCallRecord;
import com.tzq.ticketops.agent.decision.ToolIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ReadOnlyToolExecutor {

    public static final String GET_ACCOUNT_STATUS = "getAccountStatus";
    public static final String GET_USER_PERMISSIONS = "getUserPermissions";

    private static final Set<String> ALLOWED_TOOLS = Set.of(
            GET_ACCOUNT_STATUS,
            GET_USER_PERMISSIONS
    );
    private static final Set<String> ALLOWED_APP_CODES = Set.of("OA", "CRM", "ERP", "VPN");
    private static final Set<String> ACCOUNT_ARGUMENTS = Set.of("userId");
    private static final Set<String> PERMISSION_ARGUMENTS = Set.of("userId", "appCode");

    private final MockAccountStatusTool accountStatusTool;
    private final MockUserPermissionsTool permissionsTool;
    private final int maxCallsPerRequest;

    @Autowired
    public ReadOnlyToolExecutor(
            MockAccountStatusTool accountStatusTool,
            MockUserPermissionsTool permissionsTool,
            @Value("${ticketops.tools.max-calls-per-request:1}") int maxCallsPerRequest
    ) {
        if (maxCallsPerRequest != 1) {
            throw new IllegalArgumentException("maxCallsPerRequest must equal 1 for single-tool execution");
        }
        this.accountStatusTool = accountStatusTool;
        this.permissionsTool = permissionsTool;
        this.maxCallsPerRequest = maxCallsPerRequest;
    }

    public ToolExecutionResult executeSingle(
            List<ToolIntent> intents,
            String requesterId,
            TicketCategory category
    ) {
        List<ToolIntent> safeIntents = intents == null ? List.of() : new ArrayList<>(intents);
        if (safeIntents.isEmpty()) {
            throw rejected(ToolRejectionReason.MISSING_TOOL_INTENT, "none");
        }
        if (safeIntents.size() > maxCallsPerRequest) {
            throw rejected(ToolRejectionReason.TOOL_BUDGET_EXCEEDED, firstToolName(safeIntents));
        }

        ToolIntent intent = safeIntents.get(0);
        String toolName = intent == null ? null : intent.toolName();
        if (toolName == null || !ALLOWED_TOOLS.contains(toolName)) {
            throw rejected(ToolRejectionReason.UNAUTHORIZED_TOOL, toolName);
        }
        if (!toolName.equals(expectedTool(category))) {
            throw rejected(ToolRejectionReason.CATEGORY_TOOL_MISMATCH, toolName);
        }

        Map<String, String> arguments = intent.args() == null
                ? Map.of()
                : new LinkedHashMap<>(intent.args());
        return switch (toolName) {
            case GET_ACCOUNT_STATUS -> executeAccountStatus(arguments, requesterId);
            case GET_USER_PERMISSIONS -> executeUserPermissions(arguments, requesterId);
            default -> throw rejected(ToolRejectionReason.UNAUTHORIZED_TOOL, toolName);
        };
    }

    public int maxCallsPerRequest() {
        return maxCallsPerRequest;
    }

    private ToolExecutionResult executeAccountStatus(
            Map<String, String> arguments,
            String requesterId
    ) {
        requireExactArguments(arguments, ACCOUNT_ARGUMENTS, GET_ACCOUNT_STATUS);
        GetAccountStatusArgs args = new GetAccountStatusArgs(requireRequester(
                arguments.get("userId"),
                requesterId,
                GET_ACCOUNT_STATUS
        ));
        try {
            AccountStatusResult result = accountStatusTool.getAccountStatus(args.userId());
            return result(
                    GET_ACCOUNT_STATUS,
                    Map.of("userId", args.userId()),
                    result.status().name(),
                    false
            );
        } catch (RuntimeException exception) {
            throw rejected(ToolRejectionReason.TOOL_INVOCATION_FAILED, GET_ACCOUNT_STATUS);
        }
    }

    private ToolExecutionResult executeUserPermissions(
            Map<String, String> arguments,
            String requesterId
    ) {
        requireExactArguments(arguments, PERMISSION_ARGUMENTS, GET_USER_PERMISSIONS);
        String userId = requireRequester(arguments.get("userId"), requesterId, GET_USER_PERMISSIONS);
        String appCode = normalizeAppCode(arguments.get("appCode"));
        GetUserPermissionsArgs args = new GetUserPermissionsArgs(userId, appCode);
        try {
            UserPermissionsResult result = permissionsTool.getUserPermissions(args.userId(), args.appCode());
            String resultSummary = result.permissionCodes().isEmpty()
                    ? "NONE"
                    : String.join(",", result.permissionCodes());
            return result(
                    GET_USER_PERMISSIONS,
                    Map.of("userId", args.userId(), "appCode", args.appCode()),
                    resultSummary,
                    result.permissionCodes().isEmpty()
            );
        } catch (RuntimeException exception) {
            throw rejected(ToolRejectionReason.TOOL_INVOCATION_FAILED, GET_USER_PERMISSIONS);
        }
    }

    private ToolExecutionResult result(
            String toolName,
            Map<String, String> arguments,
            String resultSummary,
            boolean emptyResult
    ) {
        return new ToolExecutionResult(
                new ToolCallRecord(toolName, arguments, resultSummary),
                1,
                maxCallsPerRequest,
                emptyResult
        );
    }

    private void requireExactArguments(
            Map<String, String> arguments,
            Set<String> expectedArguments,
            String toolName
    ) {
        if (!arguments.keySet().equals(expectedArguments)
                || arguments.values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw rejected(ToolRejectionReason.INVALID_ARGUMENTS, toolName);
        }
    }

    private String requireRequester(String proposedUserId, String requesterId, String toolName) {
        if (requesterId == null || requesterId.isBlank() || !requesterId.equals(proposedUserId)) {
            throw rejected(ToolRejectionReason.REQUESTER_MISMATCH, toolName);
        }
        return requesterId;
    }

    private String normalizeAppCode(String proposedAppCode) {
        String normalized = proposedAppCode.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_APP_CODES.contains(normalized)) {
            throw rejected(ToolRejectionReason.UNSUPPORTED_APP_CODE, GET_USER_PERMISSIONS);
        }
        return normalized;
    }

    private String expectedTool(TicketCategory category) {
        if (category == null) {
            return "none";
        }
        return switch (category) {
            case ACCOUNT_LOCKED -> GET_ACCOUNT_STATUS;
            case PERMISSION_REQUEST -> GET_USER_PERMISSIONS;
            default -> "none";
        };
    }

    private String firstToolName(List<ToolIntent> intents) {
        ToolIntent first = intents.get(0);
        return first == null ? "none" : first.toolName();
    }

    private ToolExecutionRejectedException rejected(ToolRejectionReason reason, String toolName) {
        return new ToolExecutionRejectedException(reason, toolName, maxCallsPerRequest);
    }
}
