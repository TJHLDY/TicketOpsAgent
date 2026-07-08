package com.tzq.ticketops.web;

public enum ApiErrorCode {
    TICKET_NOT_FOUND("Ticket not found"),
    PENDING_ACTION_NOT_FOUND("Pending action not found"),
    PENDING_ACTION_ALREADY_REVIEWED("Pending action already reviewed"),
    INVALID_REQUEST("Invalid request");

    private final String defaultMessage;

    ApiErrorCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
