package com.tzq.ticketops.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return build(exception.status(), exception.errorCode(), exception.getMessage(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        ApiErrorCode errorCode = ApiErrorCode.INVALID_REQUEST;
        return build(HttpStatus.BAD_REQUEST, errorCode, errorCode.defaultMessage(), request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            ApiErrorCode errorCode,
            String message,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                errorCode.name(),
                message,
                request.getRequestURI(),
                Instant.now()
        ));
    }
}
