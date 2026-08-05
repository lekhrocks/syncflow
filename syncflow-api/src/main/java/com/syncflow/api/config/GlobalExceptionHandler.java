package com.syncflow.api.config;

import com.syncflow.api.dto.ErrorResponse;
import com.syncflow.common.correlation.CorrelationId;
import com.syncflow.common.exception.SyncFlowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SyncFlowException.class)
    public ResponseEntity<ErrorResponse> handleSyncFlow(SyncFlowException ex) {
        log.warn("SyncFlowException: code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(new ErrorResponse(ex.getCode(), ex.getMessage(),
                        CorrelationId.get(), ex.getHttpStatus(), Instant.now(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErrorResponse.ValidationError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Validation failed",
                        CorrelationId.get(), 400, Instant.now(), fieldErrors));
    }

    /** Malformed / unparseable JSON body → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST_BODY", "Request body could not be read: " + ex.getMessage(),
                        CorrelationId.get(), 400, Instant.now(), null));
    }

    /** Unknown URL path → 404 (Spring 6 raises NoResourceFoundException, not NoHandlerFoundException) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", ex.getMessage(),
                        CorrelationId.get(), 404, Instant.now(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred",
                        CorrelationId.get(), 500, Instant.now(), null));
    }
}
