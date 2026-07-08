package com.tzq.ticketops.agent.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class LlmAgentDecisionParser {

    private static final Set<String> ALLOWED_TOOLS = Set.of("getAccountStatus", "getUserPermissions");
    private static final Set<String> ALLOWED_APP_CODES = Set.of("OA", "CRM", "ERP", "VPN");
    private static final Set<PendingActionType> ALLOWED_PENDING_ACTIONS = Set.of(
            PendingActionType.UNLOCK_ACCOUNT,
            PendingActionType.GRANT_PERMISSION
    );

    private final ObjectMapper objectMapper;

    public LlmAgentDecisionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AgentDecision parse(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new LlmDecisionException("EMPTY_RESPONSE", "LLM returned empty content");
        }

        LlmAgentDecisionDto dto;
        try {
            dto = objectMapper.readValue(rawJson, LlmAgentDecisionDto.class);
        } catch (JsonProcessingException exception) {
            throw new LlmDecisionException("PARSE_ERROR", exception.getOriginalMessage());
        }

        TicketCategory category = parseEnum(TicketCategory.class, dto.category(), "INVALID_CATEGORY");
        TicketPriority priority = parseEnum(TicketPriority.class, dto.priority(), "INVALID_PRIORITY");
        RiskLevel riskLevel = parseEnum(RiskLevel.class, dto.riskLevel(), "INVALID_RISK_LEVEL");
        double confidence = validateConfidence(dto.confidence());
        List<ToolIntent> toolIntents = validateTools(dto.toolIntents());
        PendingActionProposal pendingActionProposal = validatePendingAction(dto.pendingActions(), riskLevel);

        return new AgentDecision(
                category,
                priority,
                riskLevel,
                defaultString(dto.sopQuery()),
                toolIntents,
                pendingActionProposal,
                defaultString(dto.internalSuggestion()),
                defaultString(dto.userReplyDraft()),
                confidence,
                reasonList(dto.reasonCode())
        );
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value, String status) {
        if (value == null || value.isBlank()) {
            throw new LlmDecisionException(status, "missing enum value");
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            throw new LlmDecisionException(status, value);
        }
    }

    private double validateConfidence(Double confidence) {
        if (confidence == null || confidence < 0.0 || confidence > 1.0) {
            throw new LlmDecisionException("INVALID_CONFIDENCE", "confidence must be between 0.0 and 1.0");
        }
        return confidence;
    }

    private List<ToolIntent> validateTools(List<LlmToolIntentDto> toolDtos) {
        if (toolDtos == null || toolDtos.isEmpty()) {
            return List.of();
        }
        List<ToolIntent> toolIntents = new ArrayList<>();
        for (LlmToolIntentDto toolDto : toolDtos) {
            if (toolDto.toolName() == null || !ALLOWED_TOOLS.contains(toolDto.toolName())) {
                throw new LlmDecisionException("UNAUTHORIZED_TOOL", String.valueOf(toolDto.toolName()));
            }
            Map<String, String> arguments = toolDto.arguments() == null ? Map.of() : toolDto.arguments();
            if (arguments.containsKey("appCode") && !ALLOWED_APP_CODES.contains(arguments.get("appCode"))) {
                throw new LlmDecisionException("INVALID_TOOL_ARGUMENT", "unsupported appCode");
            }
            toolIntents.add(new ToolIntent(toolDto.toolName(), arguments));
        }
        return List.copyOf(toolIntents);
    }

    private PendingActionProposal validatePendingAction(List<LlmPendingActionDto> actionDtos, RiskLevel riskLevel) {
        if (actionDtos == null || actionDtos.isEmpty()) {
            return null;
        }
        if (actionDtos.size() > 1) {
            throw new LlmDecisionException("TOO_MANY_PENDING_ACTIONS", "only one pending action is supported");
        }
        LlmPendingActionDto actionDto = actionDtos.get(0);
        PendingActionType actionType = parseEnum(PendingActionType.class, actionDto.actionType(), "INVALID_PENDING_ACTION");
        if (!ALLOWED_PENDING_ACTIONS.contains(actionType)) {
            throw new LlmDecisionException("UNAUTHORIZED_PENDING_ACTION", actionType.name());
        }
        if (!actionDto.requiresApproval()) {
            throw new LlmDecisionException("WRITE_ACTION_REQUIRES_APPROVAL", actionType.name());
        }
        if (riskLevel == RiskLevel.READ_ONLY) {
            throw new LlmDecisionException("READ_ONLY_WITH_PENDING_ACTION", actionType.name());
        }
        if (riskLevel == RiskLevel.REJECT) {
            throw new LlmDecisionException("REJECT_WITH_PENDING_ACTION", actionType.name());
        }
        return new PendingActionProposal(actionType, true);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private List<String> reasonList(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return List.of("llm");
        }
        return List.of(reasonCode);
    }
}
