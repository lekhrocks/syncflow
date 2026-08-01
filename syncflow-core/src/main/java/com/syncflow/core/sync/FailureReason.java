package com.syncflow.core.sync;

public record FailureReason(
        String code,
        String message,
        boolean retryable) {

    public static FailureReason transientError(String message) {
        return new FailureReason("TRANSIENT", message, true);
    }

    public static FailureReason permanentError(String message) {
        return new FailureReason("PERMANENT", message, false);
    }
}
