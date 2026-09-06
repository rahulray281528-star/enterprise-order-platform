package com.enterprise.payment.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes only after commit, so a rolled-back transaction can never tell the rest of
 * the platform that a payment succeeded.
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
                        log.error("Failed to publish to {} for key {}", event.topic(), event.key(), throwable);
                    } else {
                        log.info("Published {} to {}",
                                event.payload().getClass().getSimpleName(), event.topic());
                    }
                });
    }
}
