package com.tzq.ticketops.agent.decision;

public class LlmDecisionException extends RuntimeException {

    private final String status;

    public LlmDecisionException(String status, String message) {
        super(status + ": " + message);
        this.status = status;
    }

    public String status() {
        return status;
    }
}
