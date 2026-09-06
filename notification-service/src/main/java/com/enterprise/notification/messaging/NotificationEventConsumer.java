package com.enterprise.notification.messaging;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.event.OrderCancelledEvent;
import com.enterprise.common.event.OrderConfirmedEvent;
import com.enterprise.common.event.OrderCreatedEvent;
import com.enterprise.common.event.PaymentCompletedEvent;
import com.enterprise.common.event.PaymentFailedEvent;
import com.enterprise.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the whole order lifecycle.
 *
 * <p>This service is the clearest argument for the event-driven design: it was added
 * without a single change to order-service or payment-service. Adding an SMS or push
 * channel later is the same shape of change.</p>
 */
@Component
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;

    public NotificationEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "notification-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        notificationService.record(event.getEventId(), event.getCustomerId(), event.getOrderId(),
                "ORDER_CREATED",
                "We have received your order",
                "Your order %s for %s has been placed and is awaiting payment."
                        .formatted(event.getOrderId(), event.getTotalAmount()));
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "notification-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        notificationService.record(event.getEventId(), event.getCustomerId(), event.getOrderId(),
                "PAYMENT_COMPLETED",
                "Payment received",
                "We have received your payment of %s for order %s."
                        .formatted(event.getAmount(), event.getOrderId()));
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "notification-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        notificationService.record(event.getEventId(), event.getCustomerId(), event.getOrderId(),
                "PAYMENT_FAILED",
                "There was a problem with your payment",
                "Payment for order %s could not be completed: %s. Any reserved items have been released."
                        .formatted(event.getOrderId(), event.getReason()));
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CONFIRMED, groupId = "notification-service")
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        notificationService.record(event.getEventId(), event.getCustomerId(), event.getOrderId(),
                "ORDER_CONFIRMED",
                "Your order is confirmed",
                "Order %s is confirmed. Total paid: %s."
                        .formatted(event.getOrderId(), event.getTotalAmount()));
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        notificationService.record(event.getEventId(), event.getCustomerId(), event.getOrderId(),
                "ORDER_CANCELLED",
                "Your order has been cancelled",
                "Order %s has been cancelled: %s".formatted(event.getOrderId(), event.getReason()));
    }
}
