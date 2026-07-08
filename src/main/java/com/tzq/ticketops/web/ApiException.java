package com.tzq.ticketops.web;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ApiErrorCode errorCode;

    private ApiException(HttpStatus status, ApiErrorCode errorCode) {
        super(errorCode.defaultMessage());
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException of(HttpStatus status, ApiErrorCode errorCode) {
        return new ApiException(status, errorCode);
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode errorCode() {
        return errorCode;
    }
}
