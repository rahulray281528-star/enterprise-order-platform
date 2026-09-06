package com.enterprise.payment.messaging;

/** Carrier used to defer a Kafka publish until the database transaction commits. */
public record OutboundEvent(String topic, String key, Object payload) {
}
