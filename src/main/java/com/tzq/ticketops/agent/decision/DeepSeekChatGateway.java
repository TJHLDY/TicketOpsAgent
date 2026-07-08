package com.tzq.ticketops.agent.decision;

@FunctionalInterface
public interface DeepSeekChatGateway {
    String complete(String prompt);
}
