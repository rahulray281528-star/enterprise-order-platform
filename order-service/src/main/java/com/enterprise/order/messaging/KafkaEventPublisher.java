package com.enterprise.order.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes to Kafka only after the surrounding database transaction has committed.
 *
 * <p>Publishing inside the transaction is a classic dual-write bug: the broker would
 * receive OrderCreated even when the transaction is later rolled back, and payment would
 * start charging for an order that does not exist. Deferring to AFTER_COMMIT removes
 * that window.</p>
 *
 * <p>The remaining gap - commit succeeds but the broker is unreachable - is what a
 * transactional outbox table would close. That trade-off is discussed in
 * docs/system-design.md.</p>
 */
@Component
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(OutboundEvent event) {
        kafkaTemplate.send(event.topic(), event.key(), event.payload())
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Failed to publish {} for key {} to topic {}",
                                event.payload().getClass().getSimpleName(), event.key(), event.topic(), throwable);
                    } else {
                        log.info("Published {} for key {} to topic {}",
                                event.payload().getClass().getSimpleName(), event.key(), event.topic());
                    }
                });
    }
}
