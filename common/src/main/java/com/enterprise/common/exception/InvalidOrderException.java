package com.enterprise.common.exception;

public class InvalidOrderException extends BusinessException {

    public InvalidOrderException(String message) {
        super(message, "INVALID_ORDER", 400);
    }
}
