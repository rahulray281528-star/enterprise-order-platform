package com.enterprise.common.util;

import java.util.UUID;

/**
 * Holds the correlation id for the current thread so every log line and every error
 * response can be tied back to a single request as it crosses service boundaries.
 */
public final class RequestContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();

    private RequestContext() {
    }

    public static void setCorrelationId(String correlationId) {
        CORRELATION_ID.set(correlationId);
    }

    public static String getCorrelationId() {
        String id = CORRELATION_ID.get();
        if (id == null) {
            id = newCorrelationId();
            CORRELATION_ID.set(id);
        }
        return id;
    }

    public static String newCorrelationId() {
        return UUID.randomUUID().toString();
    }

    public static void clear() {
        CORRELATION_ID.remove();
    }
}
