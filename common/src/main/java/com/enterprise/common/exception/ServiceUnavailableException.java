package com.enterprise.common.exception;

/** Raised by Resilience4j fallbacks when a downstream dependency is not answering. */
public class ServiceUnavailableException extends BusinessException {

    public ServiceUnavailableException(String message) {
        super(message, "SERVICE_UNAVAILABLE", 503);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, "SERVICE_UNAVAILABLE", 503, cause);
    }
}
