package com.tzq.ticketops.agent.decision;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.agent.TicketPriority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class LlmAgentDecisionParser {

    private static final Set<String> ALLOWED_TOOLS = Set.of("getAccountStatus", "getUserPermissions");
    private static final Set<String> ALLOWED_APP_CODES = Set.of("OA", "CRM", "ERP", "VPN");
    private static final Set<String> ACCOUNT_TOOL_ARGUMENTS = Set.of("userId");
    private static final Set<String> PERMISSION_TOOL_ARGUMENTS = Set.of("userId", "appCode");
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
        } catch (JacksonException exception) {
            throw new LlmDecisionException("PARSE_ERROR", exception.getOriginalMessage());
        }

        TicketCategory category = parseEnum(TicketCategory.class, dto.category(), "INVALID_CATEGORY");
        TicketPriority priority = parseEnum(TicketPriority.class, dto.priority(), "INVALID_PRIORITY");
        RiskLevel riskLevel = parseEnum(RiskLevel.class, dto.riskLevel(), "INVALID_RISK_LEVEL");
        double confidence = validateConfidence(dto.confidence());
        List<ToolIntent> toolIntents = validateTools(dto.toolIntents());
        PendingActionProposal pendingActionProposal = validatePendingAction(dto.pendingActions(), category, riskLevel);

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
        if (toolDtos.size() > 1) {
            throw new LlmDecisionException("TOO_MANY_TOOL_INTENTS", "only one tool intent is supported");
        }
        List<ToolIntent> toolIntents = new ArrayList<>();
        for (LlmToolIntentDto toolDto : toolDtos) {
            if (toolDto == null || toolDto.toolName() == null || !ALLOWED_TOOLS.contains(toolDto.toolName())) {
                throw new LlmDecisionException("UNAUTHORIZED_TOOL", toolDto == null ? "null" : String.valueOf(toolDto.toolName()));
            }
            Map<String, String> arguments = toolDto.arguments() == null ? Map.of() : toolDto.arguments();
            validateToolArguments(toolDto.toolName(), arguments);
            Map<String, String> normalizedArguments = new LinkedHashMap<>(arguments);
            if (normalizedArguments.containsKey("appCode")) {
                normalizedArguments.put(
                        "appCode",
                        normalizedArguments.get("appCode").trim().toUpperCase(Locale.ROOT)
                );
            }
            if (normalizedArguments.containsKey("appCode")
                    && !ALLOWED_APP_CODES.contains(normalizedArguments.get("appCode"))) {
                throw new LlmDecisionException("INVALID_TOOL_ARGUMENT", "unsupported appCode");
            }
            toolIntents.add(new ToolIntent(toolDto.toolName(), Map.copyOf(normalizedArguments)));
        }
        return List.copyOf(toolIntents);
    }

    private void validateToolArguments(String toolName, Map<String, String> arguments) {
        Set<String> expectedArguments = "getUserPermissions".equals(toolName)
                ? PERMISSION_TOOL_ARGUMENTS
                : ACCOUNT_TOOL_ARGUMENTS;
        if (!arguments.keySet().equals(expectedArguments)
                || arguments.values().stream().anyMatch(this::isBlank)) {
            throw new LlmDecisionException("INVALID_TOOL_ARGUMENT", "arguments do not match tool schema");
        }
    }

    private PendingActionProposal validatePendingAction(
            List<LlmPendingActionDto> actionDtos,
            TicketCategory category,
            RiskLevel riskLevel
    ) {
        if (actionDtos == null || actionDtos.isEmpty()) {
            return null;
        }
        if (actionDtos.size() > 1) {
            throw new LlmDecisionException("TOO_MANY_PENDING_ACTIONS", "only one pending action is supported");
        }
        LlmPendingActionDto actionDto = actionDtos.get(0);
        if (actionDto == null) {
            throw new LlmDecisionException("INVALID_PENDING_ACTION", "null");
        }
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
        validatePendingActionCategory(category, actionType);
        return new PendingActionProposal(actionType, true);
    }

    private void validatePendingActionCategory(TicketCategory category, PendingActionType actionType) {
        boolean matches = switch (category) {
            case ACCOUNT_LOCKED -> actionType == PendingActionType.UNLOCK_ACCOUNT;
            case PERMISSION_REQUEST -> actionType == PendingActionType.GRANT_PERMISSION;
            default -> false;
        };
        if (!matches) {
            throw new LlmDecisionException("PENDING_ACTION_CATEGORY_MISMATCH", category + " cannot use " + actionType);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
