package com.openclaw.memory.adapter.in.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("bad_request", ex.getMessage(), null, Instant.now()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<ApiError> handleExternalHttpError(RestClientResponseException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError(
                        "external_service_error",
                        ex.getStatusCode() + " from external service",
                        ex.getResponseBodyAsString(),
                        Instant.now()
                ));
    }

    public record ApiError(
            String code,
            String message,
            String detail,
            Instant timestamp
    ) {
    }
}
