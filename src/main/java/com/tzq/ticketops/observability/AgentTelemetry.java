package com.tzq.ticketops.observability;

import com.tzq.ticketops.agent.PendingActionType;
import com.tzq.ticketops.agent.RiskLevel;
import com.tzq.ticketops.agent.TicketCategory;
import com.tzq.ticketops.rag.SopSearchStatus;
import com.tzq.ticketops.tools.ToolRejectionReason;

public interface AgentTelemetry {

    RequestObservation NOOP_REQUEST = new RequestObservation() {
        @Override
        public void complete(
                AgentRequestOutcome outcome,
                TicketCategory category,
                RiskLevel riskLevel
        ) {
        }

        @Override
        public void error() {
        }
    };

    AgentTelemetry NOOP = new AgentTelemetry() {
        @Override
        public RequestObservation startRequest() {
            return NOOP_REQUEST;
        }

        @Override
        public void recordRag(SopSearchStatus status, String provider) {
        }

        @Override
        public void recordToolSuccess(String toolName) {
        }

        @Override
        public void recordToolRejected(String toolName, ToolRejectionReason reason) {
        }

        @Override
        public void recordPendingAction(PendingActionType type) {
        }

        @Override
        public void recordShadow(ShadowDecisionOutcome outcome) {
        }
    };

    RequestObservation startRequest();

    void recordRag(SopSearchStatus status, String provider);

    void recordToolSuccess(String toolName);

    void recordToolRejected(String toolName, ToolRejectionReason reason);

    void recordPendingAction(PendingActionType type);

    void recordShadow(ShadowDecisionOutcome outcome);

    static AgentTelemetry noop() {
        return NOOP;
    }

    interface RequestObservation {

        void complete(
                AgentRequestOutcome outcome,
                TicketCategory category,
                RiskLevel riskLevel
        );

        void error();
    }
}
