package com.enterprise.common.exception;

public class PaymentFailedException extends BusinessException {

    public PaymentFailedException(String message) {
        super(message, "PAYMENT_FAILED", 402);
    }
}
