package com.enterprise.payment.service;

import com.enterprise.common.constant.KafkaTopics;
import com.enterprise.common.constant.PaymentStatus;
import com.enterprise.common.event.PaymentCompletedEvent;
import com.enterprise.common.event.PaymentFailedEvent;
import com.enterprise.common.exception.ResourceNotFoundException;
import com.enterprise.payment.dto.PaymentResponse;
import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.gateway.PaymentGatewayClient;
import com.enterprise.payment.messaging.OutboundEvent;
import com.enterprise.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient gatewayClient;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentGatewayClient gatewayClient,
                          ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.gatewayClient = gatewayClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Charges an order exactly once.
     *
     * <p>Idempotency is enforced by the unique constraint on payments.order_id rather
     * than by an in-memory check: two concurrent deliveries of the same
     * OrderCreatedEvent can both pass an "exists?" test, but only one can insert.</p>
     */
    @Transactional
    public PaymentResponse process(String orderId, String customerId, BigDecimal amount) {
        var existing = paymentRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            log.info("Payment for order {} already processed with status {} - skipping",
                    orderId, existing.get().getStatus());
            return PaymentResponse.from(existing.get());
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .status(PaymentStatus.INITIATED)
                .build());

        PaymentGatewayClient.GatewayResult result = gatewayClient.charge(orderId, amount);

        if (result.approved()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayReference(result.reference());
            paymentRepository.save(payment);

            eventPublisher.publishEvent(new OutboundEvent(KafkaTopics.PAYMENT_COMPLETED, orderId,
                    PaymentCompletedEvent.builder()
                            .orderId(orderId)
                            .paymentId(payment.getId())
                            .customerId(customerId)
                            .amount(amount)
                            .build()));

            log.info("Payment {} completed for order {}", payment.getId(), orderId);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(result.failureReason());
            paymentRepository.save(payment);

            // Consumed by order-service (mark failed) and inventory-service (release stock).
            eventPublisher.publishEvent(new OutboundEvent(KafkaTopics.PAYMENT_FAILED, orderId,
                    PaymentFailedEvent.builder()
                            .orderId(orderId)
                            .paymentId(payment.getId())
                            .customerId(customerId)
                            .reason(result.failureReason())
                            .build()));

            log.warn("Payment {} failed for order {}: {}",
                    payment.getId(), orderId, result.failureReason());
        }

        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(String paymentId) {
        return paymentRepository.findById(paymentId).map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId).map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId));
    }
}
