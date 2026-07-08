package com.tzq.ticketops.agent.decision;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "ticketops.agent.llm", name = "enabled", havingValue = "true")
public class DeepSeekLlmAgentDecisionService implements AgentDecisionPort {

    private final ObjectProvider<DeepSeekChatGateway> chatGatewayProvider;
    private final LlmAgentDecisionParser parser;

    public DeepSeekLlmAgentDecisionService(
            ObjectProvider<DeepSeekChatGateway> chatGatewayProvider,
            LlmAgentDecisionParser parser
    ) {
        this.chatGatewayProvider = chatGatewayProvider;
        this.parser = parser;
    }

    @Override
    public AgentDecision decide(AgentContext context) {
        DeepSeekChatGateway chatGateway = chatGatewayProvider.getIfAvailable();
        if (chatGateway == null) {
            throw new LlmDecisionException("NO_LLM_GATEWAY", "DeepSeek gateway is not available");
        }
        return parser.parse(chatGateway.complete(promptFor(context)));
    }

    private String promptFor(AgentContext context) {
        return """
                Return json for the following ticket.
                Allowed category values: ACCOUNT_LOCKED, LOGIN_FAILED, PERMISSION_REQUEST, MFA_ISSUE, UNKNOWN.
                Allowed priority values: P1, P2, P3.
                Allowed riskLevel values: READ_ONLY, NEEDS_APPROVAL, REJECT.
                Allowed toolName values: getAccountStatus, getUserPermissions.
                Allowed pending action values: UNLOCK_ACCOUNT, GRANT_PERMISSION.
                Allowed appCode values: OA, CRM, ERP, VPN.
                Required JSON fields:
                category, priority, riskLevel, confidence, sopQuery, toolIntents, pendingActions, internalSuggestion, userReplyDraft, reasonCode.
                Ticket requesterId: %s
                Ticket title: %s
                Ticket description: %s
                """.formatted(context.requesterId(), context.title(), context.description());
    }
}
