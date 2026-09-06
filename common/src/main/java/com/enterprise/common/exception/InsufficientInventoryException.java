package com.enterprise.common.exception;

public class InsufficientInventoryException extends BusinessException {

    public InsufficientInventoryException(String message) {
        super(message, "INSUFFICIENT_INVENTORY", 409);
    }

    public InsufficientInventoryException(String productId, int requested, int available) {
        super(String.format("Insufficient inventory for product %s: requested %d, available %d",
                productId, requested, available), "INSUFFICIENT_INVENTORY", 409);
    }
}
