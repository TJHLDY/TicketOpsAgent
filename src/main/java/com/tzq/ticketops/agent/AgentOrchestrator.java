package com.tzq.ticketops.agent;

import com.tzq.ticketops.agent.decision.AgentContext;
import com.tzq.ticketops.agent.decision.AgentDecision;
import com.tzq.ticketops.agent.decision.AgentDecisionPort;
import com.tzq.ticketops.agent.decision.AgentMode;
import com.tzq.ticketops.agent.decision.DeepSeekLlmAgentDecisionService;
import com.tzq.ticketops.agent.decision.DeterministicAgentDecisionService;
import com.tzq.ticketops.agent.decision.LlmDecisionException;
import com.tzq.ticketops.agent.decision.ToolIntent;
import com.tzq.ticketops.rag.SopSearchService;
import com.tzq.ticketops.rag.SopSearchResult;
import com.tzq.ticketops.rag.SopSearchStatus;
import com.tzq.ticketops.tools.MockAccountStatusTool;
import com.tzq.ticketops.tools.MockUserPermissionsTool;
import com.tzq.ticketops.tools.ReadOnlyToolExecutor;
import com.tzq.ticketops.tools.ToolExecutionRejectedException;
import com.tzq.ticketops.tools.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AgentOrchestrator {

    private static final String DEFAULT_LLM_PROVIDER = "deepseek";
    private static final String DEFAULT_LLM_MODEL = "deepseek-v4-flash";
    private static final String DEFAULT_PROMPT_VERSION = "deepseek-shadow-v2";
    private static final String DEFAULT_SCHEMA_VERSION = "agent-decision-v1";

    private final SopSearchService sopSearchService;
    private final ReadOnlyToolExecutor toolExecutor;
    private final AgentMode agentMode;
    private final AgentDecisionPort decisionPort;
    private final AgentDecisionPort shadowDecisionPort;
    private final String llmProvider;
    private final String llmModel;
    private final String promptVersion;
    private final String schemaVersion;

    @Autowired
    public AgentOrchestrator(
            SopSearchService sopSearchService,
            ReadOnlyToolExecutor toolExecutor,
            @Value("${ticketops.agent.mode:deterministic}") String agentModeValue,
            @Value("${ticketops.agent.llm.provider:deepseek}") String llmProvider,
            @Value("${ticketops.agent.llm.model:deepseek-v4-flash}") String llmModel,
            @Value("${ticketops.agent.llm.prompt-version:deepseek-shadow-v2}") String promptVersion,
            @Value("${ticketops.agent.llm.schema-version:agent-decision-v1}") String schemaVersion,
            ObjectProvider<DeepSeekLlmAgentDecisionService> shadowDecisionPortProvider
    ) {
        this(
                parseAgentMode(agentModeValue),
                new DeterministicAgentDecisionService(),
                shadowDecisionPortProvider.getIfAvailable(),
                sopSearchService,
                toolExecutor,
                llmProvider,
                llmModel,
                promptVersion,
                schemaVersion
        );
    }

    private AgentOrchestrator(
            AgentMode agentMode,
            AgentDecisionPort decisionPort,
            AgentDecisionPort shadowDecisionPort,
            SopSearchService sopSearchService,
            ReadOnlyToolExecutor toolExecutor,
            String llmProvider,
            String llmModel,
            String promptVersion,
            String schemaVersion
    ) {
        this.sopSearchService = sopSearchService;
        this.toolExecutor = toolExecutor;
        this.agentMode = agentMode;
        this.decisionPort = decisionPort;
        this.shadowDecisionPort = shadowDecisionPort;
        this.llmProvider = llmProvider;
        this.llmModel = llmModel;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
    }

    public static AgentOrchestrator createDefault() {
        return new AgentOrchestrator(
                AgentMode.DETERMINISTIC,
                new DeterministicAgentDecisionService(),
                null,
                SopSearchService.createOffline(0.30),
                new ReadOnlyToolExecutor(new MockAccountStatusTool(), new MockUserPermissionsTool(), 1),
                DEFAULT_LLM_PROVIDER,
                DEFAULT_LLM_MODEL,
                DEFAULT_PROMPT_VERSION,
                DEFAULT_SCHEMA_VERSION
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
                new ReadOnlyToolExecutor(accountStatusTool, permissionsTool, 1),
                DEFAULT_LLM_PROVIDER,
                DEFAULT_LLM_MODEL,
                DEFAULT_PROMPT_VERSION,
                DEFAULT_SCHEMA_VERSION
        );
    }

    public static AgentOrchestrator createWithPrimaryDecision(
            AgentDecisionPort decisionPort,
            SopSearchService sopSearchService,
            ReadOnlyToolExecutor toolExecutor
    ) {
        return new AgentOrchestrator(
                AgentMode.DETERMINISTIC,
                decisionPort,
                null,
                sopSearchService,
                toolExecutor,
                DEFAULT_LLM_PROVIDER,
                DEFAULT_LLM_MODEL,
                DEFAULT_PROMPT_VERSION,
                DEFAULT_SCHEMA_VERSION
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
            traceEvents.add(shadowDecisionEvent(context));
        }

        if (riskLevel == RiskLevel.REJECT) {
            return rejectedResponse(category, priority, riskLevel, traceEvents);
        }

        if (category == TicketCategory.ACCOUNT_LOCKED) {
            return handleAccountLocked(
                    request,
                    decision.sopQuery(),
                    decision.toolIntents(),
                    traceEvents,
                    category,
                    priority,
                    riskLevel
            );
        }
        if (category == TicketCategory.PERMISSION_REQUEST) {
            return handlePermissionRequest(
                    request,
                    decision.sopQuery(),
                    decision.toolIntents(),
                    traceEvents,
                    category,
                    priority,
                    riskLevel
            );
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
            String sopQuery,
            List<ToolIntent> toolIntents,
            List<TraceEvent> traceEvents,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel
    ) {
        String text = request.title() + "\n" + request.description();
        SopSearchResult searchResult = sopSearchService.search(ragQuery(sopQuery, text));
        if (searchResult.status() != SopSearchStatus.ACCEPTED) {
            return ragRejectedResponse(category, priority, riskLevel, traceEvents, searchResult);
        }
        SopReference sop = searchResult.reference().orElseThrow();
        traceEvents.add(acceptedRagTrace(sop, searchResult));

        traceEvents.add(toolDecisionTrace(toolIntents));
        ToolExecutionResult executionResult;
        try {
            executionResult = toolExecutor.executeSingle(toolIntents, request.requesterId(), category);
        } catch (ToolExecutionRejectedException exception) {
            return toolRejectedResponse(category, priority, riskLevel, traceEvents, exception);
        }
        ToolCallRecord toolCall = executionResult.toolCall();
        String appCode = toolCall.arguments().get("appCode");
        String resultSummary = toolCall.resultSummary();
        traceEvents.add(successfulToolTrace(executionResult));

        List<PendingAction> pendingActions = new ArrayList<>();
        String suggestion;
        String replyDraft;
        if (executionResult.emptyResult()) {
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

    private AgentResponse ragRejectedResponse(
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel,
            List<TraceEvent> traceEvents,
            SopSearchResult searchResult
    ) {
        traceEvents.add(new TraceEvent(
                "RAG_REJECT",
                "reason=" + searchResult.status()
                        + ", bestSimilarity=" + searchResult.bestScore()
                        + ", threshold=" + searchResult.threshold()
                        + ", provider=" + searchResult.embeddingProvider()
        ));
        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(),
                List.of(),
                "SOP 检索相似度低于可信阈值，未调用工具或生成待执行动作，建议转人工支持。",
                "您好，当前资料与您的问题匹配度不足，已停止自动处理并建议由 IT 支持人员人工核实。",
                List.of(),
                List.copyOf(traceEvents)
        );
    }

    private AgentResponse handleAccountLocked(
            AgentRequest request,
            String sopQuery,
            List<ToolIntent> toolIntents,
            List<TraceEvent> traceEvents,
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel
    ) {
        SopSearchResult searchResult = sopSearchService.search(ragQuery(
                sopQuery,
                request.title() + "\n" + request.description()
        ));
        if (searchResult.status() != SopSearchStatus.ACCEPTED) {
            return ragRejectedResponse(category, priority, riskLevel, traceEvents, searchResult);
        }
        SopReference sop = searchResult.reference().orElseThrow();
        traceEvents.add(acceptedRagTrace(sop, searchResult));

        traceEvents.add(toolDecisionTrace(toolIntents));
        ToolExecutionResult executionResult;
        try {
            executionResult = toolExecutor.executeSingle(toolIntents, request.requesterId(), category);
        } catch (ToolExecutionRejectedException exception) {
            return toolRejectedResponse(category, priority, riskLevel, traceEvents, exception);
        }
        ToolCallRecord toolCall = executionResult.toolCall();
        traceEvents.add(successfulToolTrace(executionResult));

        List<PendingAction> pendingActions = new ArrayList<>();
        String suggestion;
        String replyDraft;
        if ("LOCKED".equals(toolCall.resultSummary())) {
            suggestion = "建议提交账号解锁申请，由 IT 支持人员核验身份和风险后执行解锁。";
            replyDraft = "您好，已查询到您的账号已锁定。我们建议先提交账号解锁申请，待 IT 支持人员确认后处理。";
            pendingActions.add(new PendingAction(
                    PendingActionType.UNLOCK_ACCOUNT,
                    "等待人工确认后解锁账号 " + request.requesterId()
            ));
        } else {
            suggestion = "只读查询显示账号状态为 " + toolCall.resultSummary()
                    + "，未生成账号解锁动作，建议人工核对登录失败原因。";
            replyDraft = "您好，当前查询结果未显示账号处于锁定状态。IT 支持人员会继续核对登录失败原因。";
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

    private TraceEvent acceptedRagTrace(SopReference sop, SopSearchResult searchResult) {
        return new TraceEvent(
                "RAG_RETRIEVE",
                "status=ACCEPTED"
                        + ", docId=" + sop.id()
                        + ", doc=" + sop.title()
                        + ", source=" + sop.source()
                        + ", similarity=" + sop.similarity()
                        + ", threshold=" + searchResult.threshold()
                        + ", provider=" + searchResult.embeddingProvider()
        );
    }

    private String ragQuery(String sopQuery, String userText) {
        if (sopQuery == null || sopQuery.isBlank()) {
            return userText;
        }
        return sopQuery + "\n" + userText;
    }

    private TraceEvent toolDecisionTrace(List<ToolIntent> toolIntents) {
        String requestedTool = toolIntents == null || toolIntents.isEmpty() || toolIntents.get(0) == null
                ? "none"
                : String.valueOf(toolIntents.get(0).toolName());
        int intentCount = toolIntents == null ? 0 : toolIntents.size();
        return new TraceEvent(
                "TOOL_DECISION",
                "requestedTool=" + requestedTool
                        + ", intentCount=" + intentCount
                        + ", budgetLimit=" + toolExecutor.maxCallsPerRequest()
        );
    }

    private TraceEvent successfulToolTrace(ToolExecutionResult result) {
        return new TraceEvent(
                "TOOL_CALL",
                "tool=" + result.toolCall().toolName()
                        + ", validated=true"
                        + ", budgetUsed=" + result.budgetUsed()
                        + ", budgetLimit=" + result.budgetLimit()
                        + ", result=" + result.toolCall().resultSummary()
        );
    }

    private AgentResponse toolRejectedResponse(
            TicketCategory category,
            TicketPriority priority,
            RiskLevel riskLevel,
            List<TraceEvent> traceEvents,
            ToolExecutionRejectedException exception
    ) {
        traceEvents.add(new TraceEvent(
                "TOOL_REJECT",
                "reason=" + exception.reason()
                        + ", tool=" + exception.toolName()
                        + ", budgetLimit=" + exception.budgetLimit()
        ));
        return new AgentResponse(
                category,
                priority,
                riskLevel,
                List.of(),
                List.of(),
                "只读工具请求未通过后端安全校验，未执行工具或生成待确认动作，建议转人工支持。",
                "您好，当前自动查询请求未通过安全校验，已停止处理并转由 IT 支持人员人工核实。",
                List.of(),
                List.copyOf(traceEvents)
        );
    }

    private TraceEvent shadowTraceEvent(AgentDecision shadowDecision, long latencyMs) {
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
                        + ", llm_status=ACCEPTED"
                        + ", fallback_reason=none"
                        + ", fallback_to=none"
                        + shadowAuditDetail(latencyMs)
                        + ", validation_errors=none"
        );
    }

    private TraceEvent shadowDecisionEvent(AgentContext context) {
        if (shadowDecisionPort == null) {
            return skippedShadowTraceEvent();
        }
        long startedAt = System.nanoTime();
        try {
            return shadowTraceEvent(shadowDecisionPort.decide(context), elapsedMillis(startedAt));
        } catch (LlmDecisionException exception) {
            return failedShadowTraceEvent(exception.status(), elapsedMillis(startedAt));
        } catch (RuntimeException exception) {
            return failedShadowTraceEvent("API_ERROR", elapsedMillis(startedAt));
        }
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
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

    private TraceEvent failedShadowTraceEvent(String llmStatus, long latencyMs) {
        return new TraceEvent(
                "LLM_SHADOW_FAILED",
                "llm_status=" + llmStatus
                        + ", fallback_reason=" + llmStatus
                        + ", fallback_to=DETERMINISTIC"
                        + shadowAuditDetail(latencyMs)
                        + ", validation_errors=" + llmStatus
        );
    }

    private String shadowAuditDetail(long latencyMs) {
        return ", provider=" + llmProvider
                + ", model=" + llmModel
                + ", prompt_version=" + promptVersion
                + ", schema_version=" + schemaVersion
                + ", latency_ms=" + latencyMs
                + ", final_decision_source=DETERMINISTIC"
                + ", user_visible_changed=false";
    }
}
