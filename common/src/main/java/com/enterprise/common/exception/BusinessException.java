package com.enterprise.common.exception;

/**
 * Base class for all expected, business-level failures. Carries a machine readable
 * error code and the HTTP status the API should answer with, so the global handler
 * never has to guess.
 */
public class BusinessException extends RuntimeException {

    private final String errorCode;
    private final int status;

    public BusinessException(String message, String errorCode, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public BusinessException(String message, String errorCode, int status, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }
}
