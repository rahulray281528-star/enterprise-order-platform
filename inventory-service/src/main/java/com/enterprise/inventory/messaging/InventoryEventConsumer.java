package com.enterprise.inventory.messaging;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.event.OrderCancelledEvent;
import com.enterprise.common.event.PaymentCompletedEvent;
import com.enterprise.common.event.PaymentFailedEvent;
import com.enterprise.inventory.service.InventoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inventory reacts to the outcome of payment rather than being told what to do by the
 * order service. This keeps the compensating action (releasing stock) close to the data
 * it compensates, and means a payment failure still frees stock even if the order
 * service is down at that moment.
 */
@Component
@Slf4j
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    public InventoryEventConsumer(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED, groupId = "inventory-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment completed for order {} - confirming stock consumption", event.getOrderId());
        inventoryService.confirm(event.getOrderId());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "inventory-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed for order {} ({}) - releasing reserved stock",
                event.getOrderId(), event.getReason());
        inventoryService.release(event.getOrderId());
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "inventory-service")
    public void onOrderCancelled(OrderCancelledEvent event) {
        log.info("Order {} cancelled ({}) - releasing reserved stock",
                event.getOrderId(), event.getReason());
        inventoryService.release(event.getOrderId());
    }
}
