package com.enterprise.common.exception;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", 403);
    }
}
