package com.enterprise.payment.messaging;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.event.OrderCreatedEvent;
import com.enterprise.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Payment is triggered by the order-created event rather than by a synchronous call, so
 * a slow gateway never blocks the customer's checkout request.
 */
@Component
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;

    public PaymentEventConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Order {} created for {} - initiating payment", event.getOrderId(), event.getTotalAmount());
        paymentService.process(event.getOrderId(), event.getCustomerId(), event.getTotalAmount());
    }
}
