package com.syncflow.common.exception;

import lombok.Getter;

@Getter
public class SyncFlowException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public SyncFlowException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public SyncFlowException(String code, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static SyncFlowException notFound(String entity, Object id) {
        return new SyncFlowException("NOT_FOUND", entity + " not found: " + id, 404);
    }

    public static SyncFlowException badRequest(String message) {
        return new SyncFlowException("BAD_REQUEST", message, 400);
    }

    public static SyncFlowException conflict(String message) {
        return new SyncFlowException("CONFLICT", message, 409);
    }

    public static SyncFlowException internal(String message, Throwable cause) {
        return new SyncFlowException("INTERNAL_ERROR", message, 500, cause);
    }
}
