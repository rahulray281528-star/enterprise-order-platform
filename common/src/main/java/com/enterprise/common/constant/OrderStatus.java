package com.enterprise.common.constant;

/** Lifecycle states of an order. Transitions are enforced in the order service. */
public enum OrderStatus {
    CREATED,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    COMPLETED
}
