package com.enterprise.common.constant;

/** Single source of truth for topic names shared by producers and consumers. */
public final class KafkaTopics {

    public static final String ORDER_CREATED = "order-created";
    public static final String ORDER_CONFIRMED = "order-confirmed";
    public static final String ORDER_CANCELLED = "order-cancelled";
    public static final String INVENTORY_RESERVED = "inventory-reserved";
    public static final String INVENTORY_RESERVATION_FAILED = "inventory-reservation-failed";
    public static final String PAYMENT_COMPLETED = "payment-completed";
    public static final String PAYMENT_FAILED = "payment-failed";

    /** Suffix appended by Spring Kafka's DeadLetterPublishingRecoverer. */
    public static final String DLT_SUFFIX = ".DLT";

    private KafkaTopics() {
    }
}
