package com.enterprise.order.messaging;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.event.PaymentCompletedEvent;
import com.enterprise.common.event.PaymentFailedEvent;
import com.enterprise.order.entity.ProcessedEvent;
import com.enterprise.order.repository.ProcessedEventRepository;
import com.enterprise.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies payment outcomes to the order aggregate.
 *
 * <p>Every handler is idempotent. Kafka guarantees at-least-once delivery, so after a
 * consumer restart or partition rebalance the same event can arrive again; the
 * processed_events table makes the second delivery a no-op.</p>
 */
@Component
@Slf4j
public class OrderEventConsumer {

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;

    public OrderEventConsumer(OrderService orderService, ProcessedEventRepository processedEventRepository) {
        this.orderService = orderService;
        this.processedEventRepository = processedEventRepository;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "order-service")
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (alreadyProcessed(event.getEventId(), "PaymentCompletedEvent")) {
            return;
        }
        log.info("Payment {} completed for order {}", event.getPaymentId(), event.getOrderId());
        orderService.markConfirmed(event.getOrderId(), event.getPaymentId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service")
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (alreadyProcessed(event.getEventId(), "PaymentFailedEvent")) {
            return;
        }
        log.info("Payment failed for order {}: {}", event.getOrderId(), event.getReason());
        orderService.markPaymentFailed(event.getOrderId(), event.getReason());
    }

    private boolean alreadyProcessed(String eventId, String type) {
        if (eventId == null) {
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.info("Skipping duplicate delivery of {} ({})", type, eventId);
            return true;
        }
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(type)
                .build());
        return false;
    }
}
