package com.tzq.ticketops.agent;

import com.tzq.ticketops.agent.decision.AgentContext;
import com.tzq.ticketops.agent.decision.AgentDecision;
import com.tzq.ticketops.agent.decision.AgentDecisionPort;
import com.tzq.ticketops.agent.decision.AgentMode;
import com.tzq.ticketops.agent.decision.DeterministicAgentDecisionService;
import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.tools.AccountStatusResult;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import com.tzq.ticketops.tools.UserPermissionsResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AgentOrchestrator {

    private final SopSearchService sopSearchService;
    private final MockAccountStatusTool accountStatusTool;
    private final MockUserPermissionsTool permissionsTool;
    private final AgentMode agentMode;
    private final AgentDecisionPort decisionPort;
    private final AgentDecisionPort shadowDecisionPort;

    @Autowired
    public AgentOrchestrator(
            SopSearchService sopSearchService,
            MockAccountStatusTool accountStatusTool,
            MockUserPermissionsTool permissionsTool,
            @Value("${ticketops.agent.mode:deterministic}") String agentModeValue
    ) {
        this(
                parseAgentMode(agentModeValue),
                new DeterministicAgentDecisionService(),
                null,
                sopSearchService,
                accountStatusTool,
                permissionsTool
        );
    }

    private AgentOrchestrator(
            AgentMode agentMode,
            AgentDecisionPort decisionPort,
            AgentDecisionPort shadowDecisionPort,
            SopSearchService sopSearchService,
            MockAccountStatusTool accountStatusTool,
            MockUserPermissionsTool permissionsTool
    ) {
        this.sopSearchService = sopSearchService;
        this.accountStatusTool = accountStatusTool;
        this.permissionsTool = permissionsTool;
        this.agentMode = agentMode;
        this.decisionPort = decisionPort;
        this.shadowDecisionPort = shadowDecisionPort;
    }

    public static AgentOrchestrator createDefault() {
        return new AgentOrchestrator(
                new SopSearchService(),
                new MockAccountStatusTool(),
                new MockUserPermissionsTool(),
                AgentMode.DETERMINISTIC.name()
        );
    }

    public static AgentOrchestrator createWithDecisionMode(
            AgentMode agentMode,
            AgentDecisionPort shadowDecisionPort,
            SopSearchService sopSearchService,
            MockAccountStatusTool accountStatusTool,
            MockUserPermissionsTool permissionsTool
    ) {
        return new AgentOrchestrator(
                agentMode,
                new DeterministicAgentDecisionService(),
                shadowDecisionPort,
                sopSearchService,
                accountStatusTool,
                permissionsTool
        );
    }

    public AgentResponse handle(AgentRequest request) {
        AgentContext context = new AgentContext(request.requesterId(), request.title(), request.description());
        AgentDecision decision = decisionPort.decide(context);
        TicketCategory category = decision.category();
        TicketPriority priority = decision.priority();
        RiskLevel riskLevel = decision.riskLevel();

        List<TraceEvent> traceEvents = new ArrayList<>();
        traceEvents.add(new TraceEvent("CLASSIFY", "category=" + category + ", priority=" + priority + ", risk=" + riskLevel));
        if (agentMode == AgentMode.SHADOW) {
            traceEvents.add(shadowDecisionPort == null
                    ? skippedShadowTraceEvent()
                    : shadowTraceEvent(shadowDecisionPort.decide(context)));
        }

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

    private TraceEvent shadowTraceEvent(AgentDecision shadowDecision) {
        String toolIntents = shadowDecision.toolIntents().stream()
                .map(intent -> intent.toolName())
                .collect(Collectors.joining(","));
        if (toolIntents.isBlank()) {
            toolIntents = "none";
        }
        return new TraceEvent(
                "LLM_SHADOW",
                "category=" + shadowDecision.category()
                        + ", priority=" + shadowDecision.priority()
                        + ", risk=" + shadowDecision.riskLevel()
                        + ", confidence=" + shadowDecision.confidence()
                        + ", toolIntents=" + toolIntents
        );
    }

    private static AgentMode parseAgentMode(String agentModeValue) {
        return AgentMode.valueOf(agentModeValue.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }

    private TraceEvent skippedShadowTraceEvent() {
        return new TraceEvent(
                "LLM_SHADOW_SKIPPED",
                "agentMode=SHADOW, reason=no_shadow_decision_port"
        );
    }
}
