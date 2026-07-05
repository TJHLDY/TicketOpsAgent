package com.tzq.ticketops.agent;

import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.tools.AccountStatusResult;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import com.tzq.ticketops.tools.UserPermissionsResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AgentOrchestrator {

    private final SopSearchService sopSearchService;
    private final MockAccountStatusTool accountStatusTool;
    private final MockUserPermissionsTool permissionsTool;

    public AgentOrchestrator(
            SopSearchService sopSearchService,
            MockAccountStatusTool accountStatusTool,
            MockUserPermissionsTool permissionsTool
    ) {
        this.sopSearchService = sopSearchService;
        this.accountStatusTool = accountStatusTool;
        this.permissionsTool = permissionsTool;
    }

    public static AgentOrchestrator createDefault() {
        return new AgentOrchestrator(new SopSearchService(), new MockAccountStatusTool(), new MockUserPermissionsTool());
    }

    public AgentResponse handle(AgentRequest request) {
        String text = request.title() + "\n" + request.description();
        TicketCategory category = classify(text);
        TicketPriority priority = priorityFor(category);
        RiskLevel riskLevel = riskFor(category, text);

        List<TraceEvent> traceEvents = new ArrayList<>();
        traceEvents.add(new TraceEvent("CLASSIFY", "category=" + category + ", priority=" + priority + ", risk=" + riskLevel));

        if (riskLevel == RiskLevel.REJECT) {
            return rejectedResponse(category, priority, riskLevel, traceEvents);
        }

        if (category == TicketCategory.ACCOUNT_LOCKED) {
            return handleAccountLocked(request, traceEvents, category, priority, riskLevel);
        }
        if (category == TicketCategory.PERMISSION_REQUEST) {
            return handlePermissionRequest(request, traceEvents, category, priority, riskLevel);
        }

        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(),
                List.of(),
                "当前工单暂未匹配到可自动辅助处理的账号权限 SOP，建议转人工支持。",
                "您好，您的问题已记录。当前信息不足以给出可靠处理建议，IT 支持人员会继续跟进。",
                List.of(),
                traceEvents
        );
    }

    private AgentResponse handlePermissionRequest(
            AgentRequest request,
            List<TraceEvent> traceEvents,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel
    ) {
        String text = request.title() + "\n" + request.description();
        SopReference sop = sopSearchService.findBest(text);
        traceEvents.add(new TraceEvent("RAG_RETRIEVE", "doc=" + sop.title() + ", similarity=" + sop.similarity()));

        String appCode = extractAppCode(text);
        UserPermissionsResult permissions = permissionsTool.getUserPermissions(request.requesterId(), appCode);
        String resultSummary = permissions.permissionCodes().isEmpty()
                ? "NONE"
                : String.join(",", permissions.permissionCodes());
        ToolCallRecord toolCall = new ToolCallRecord(
                "getUserPermissions",
                Map.of("userId", request.requesterId(), "appCode", appCode),
                resultSummary
        );
        traceEvents.add(new TraceEvent("TOOL_CALL", "tool=getUserPermissions, result=" + resultSummary));

        List<PendingAction> pendingActions = new ArrayList<>();
        String suggestion;
        String replyDraft;
        if (permissions.permissionCodes().isEmpty()) {
            suggestion = "未查询到该用户在 " + appCode + " 的现有权限，建议提交权限申请并等待审批。";
            replyDraft = "您好，当前未查询到您在 " + appCode + " 的权限记录。建议提交权限申请，待审批通过后由 IT 支持人员处理。";
            pendingActions.add(new PendingAction(
                    PendingActionType.GRANT_PERMISSION,
                    "等待人工确认后为账号 " + request.requesterId() + " 申请 " + appCode + " 权限"
            ));
        } else {
            suggestion = "已查询到该用户在 " + appCode + " 的权限：" + resultSummary + "，建议核对系统侧角色缓存或转人工排查异常。";
            replyDraft = "您好，已查询到您在 " + appCode + " 已存在相关权限。我们会继续核对系统侧角色缓存或访问异常。";
        }
        traceEvents.add(new TraceEvent("DRAFT_GENERATE", "suggestion and reply draft generated"));
        if (!pendingActions.isEmpty()) {
            traceEvents.add(new TraceEvent("PENDING_ACTION", "type=" + pendingActions.get(0).type()));
        }

        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(sop),
                List.of(toolCall),
                suggestion,
                replyDraft,
                List.copyOf(pendingActions),
                List.copyOf(traceEvents)
        );
    }

    private AgentResponse rejectedResponse(
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel,
            List<TraceEvent> traceEvents
    ) {
        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(),
                List.of(),
                "该请求涉及越权、绕过审批或无法可靠匹配 SOP，建议转人工安全审核。",
                "您好，该请求当前无法处理，涉及权限或审批边界的问题需要由 IT 支持人员人工审核。",
                List.of(),
                List.copyOf(traceEvents)
        );
    }

    private AgentResponse handleAccountLocked(
            AgentRequest request,
            List<TraceEvent> traceEvents,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel
    ) {
        SopReference sop = sopSearchService.findBest(request.title() + "\n" + request.description());
        traceEvents.add(new TraceEvent("RAG_RETRIEVE", "doc=" + sop.title() + ", similarity=" + sop.similarity()));

        AccountStatusResult accountStatus = accountStatusTool.getAccountStatus(request.requesterId());
        ToolCallRecord toolCall = new ToolCallRecord(
                "getAccountStatus",
                Map.of("userId", request.requesterId()),
                accountStatus.status().name()
        );
        traceEvents.add(new TraceEvent("TOOL_CALL", "tool=getAccountStatus, result=" + accountStatus.status()));

        String suggestion = "建议提交账号解锁申请，由 IT 支持人员核验身份和风险后执行解锁。";
        String replyDraft = "您好，已查询到您的账号已锁定。我们建议先提交账号解锁申请，待 IT 支持人员确认后处理。";
        traceEvents.add(new TraceEvent("DRAFT_GENERATE", "suggestion and reply draft generated"));

        PendingAction pendingAction = new PendingAction(
                PendingActionType.UNLOCK_ACCOUNT,
                "等待人工确认后解锁账号 " + request.requesterId()
        );
        traceEvents.add(new TraceEvent("PENDING_ACTION", "type=" + pendingAction.type()));

        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(sop),
                List.of(toolCall),
                suggestion,
                replyDraft,
                List.of(pendingAction),
                List.copyOf(traceEvents)
        );
    }

    private TicketCategory classify(String text) {
        if (text.contains("锁定") || text.contains("账号已锁")) {
            return TicketCategory.ACCOUNT_LOCKED;
        }
        if (text.contains("MFA") || text.contains("验证码") || text.contains("多因素")) {
            return TicketCategory.MFA_ISSUE;
        }
        if (text.contains("权限") || text.contains("无权访问")) {
            return TicketCategory.PERMISSION_REQUEST;
        }
        if (text.contains("登录") || text.contains("登陆")) {
            return TicketCategory.LOGIN_FAILED;
        }
        return TicketCategory.UNKNOWN;
    }

    private TicketPriority priorityFor(TicketCategory category) {
        return switch (category) {
            case ACCOUNT_LOCKED, MFA_ISSUE -> TicketPriority.P2;
            case LOGIN_FAILED, PERMISSION_REQUEST -> TicketPriority.P3;
            case UNKNOWN -> TicketPriority.P3;
        };
    }

    private RiskLevel riskFor(TicketCategory category, String text) {
        if (containsUnsafePrivilegeRequest(text)) {
            return RiskLevel.REJECT;
        }
        return switch (category) {
            case ACCOUNT_LOCKED, MFA_ISSUE, PERMISSION_REQUEST -> RiskLevel.NEEDS_APPROVAL;
            case LOGIN_FAILED -> RiskLevel.READ_ONLY;
            case UNKNOWN -> RiskLevel.REJECT;
        };
    }

    private boolean containsUnsafePrivilegeRequest(String text) {
        return text.contains("绕过审批")
                || text.contains("管理员权限")
                || text.contains("生产系统管理员")
                || text.contains("越权");
    }

    private String extractAppCode(String text) {
        if (text.contains("CRM")) {
            return "CRM";
        }
        if (text.contains("ERP")) {
            return "ERP";
        }
        if (text.contains("VPN")) {
            return "VPN";
        }
        return "OA";
    }
}
