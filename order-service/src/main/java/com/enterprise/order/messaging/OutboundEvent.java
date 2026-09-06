package com.enterprise.order.messaging;

/**
 * Internal carrier used to defer a Kafka publish until the database transaction commits.
 *
 * @param topic   destination topic
 * @param key     partition key - always the order id, so all events for one order are ordered
 * @param payload the domain event
 */
public record OutboundEvent(String topic, String key, Object payload) {
}
