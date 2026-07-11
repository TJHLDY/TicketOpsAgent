package com.tzq.ticketops.tools;

public final class ToolExecutionRejectedException extends RuntimeException {

    private final ToolRejectionReason reason;
    private final String toolName;
    private final int budgetLimit;

    public ToolExecutionRejectedException(
            ToolRejectionReason reason,
            String toolName,
            int budgetLimit
    ) {
        super(reason.name());
        this.reason = reason;
        this.toolName = toolName == null || toolName.isBlank() ? "none" : toolName;
        this.budgetLimit = budgetLimit;
    }

    public ToolRejectionReason reason() {
        return reason;
    }

    public String toolName() {
        return toolName;
    }

    public int budgetLimit() {
        return budgetLimit;
    }
}

