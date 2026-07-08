package com.tzq.ticketops.agent.decision;

public interface AgentDecisionPort {
    AgentDecision decide(AgentContext context);
}
