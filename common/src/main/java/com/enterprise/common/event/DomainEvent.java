package com.enterprise.common.event;

import java.time.Instant;

/**
 * Contract shared by every Kafka payload on the platform.
 *
 * <p>{@code eventId} is what makes consumers idempotent: a consumer that has already
 * processed an eventId skips the redelivery instead of double-charging a customer.</p>
 */
public interface DomainEvent {

    String getEventId();

    Instant getOccurredAt();

    String getCorrelationId();
}
