package com.tzq.ticketops.agent;

import java.util.Optional;

public interface AgentExecutionLogRepository {

    void save(AgentExecutionLog log);

    Optional<AgentExecutionLog> findByTicketId(String ticketId);
}
