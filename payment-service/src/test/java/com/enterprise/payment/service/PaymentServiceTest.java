package com.enterprise.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.common.constant.PaymentStatus;
import com.enterprise.payment.dto.PaymentResponse;
import com.enterprise.payment.entity.Payment;
import com.enterprise.payment.gateway.PaymentGatewayClient;
import com.enterprise.payment.messaging.OutboundEvent;
import com.enterprise.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayClient gatewayClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("an approved charge marks the payment COMPLETED and publishes PaymentCompletedEvent")
    void approvedChargeCompletesPayment() {
        when(paymentRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gatewayClient.charge(eq("order-1"), any(BigDecimal.class)))
                .thenReturn(new PaymentGatewayClient.GatewayResult(true, "PAY-ABC123", null));

        PaymentResponse response = paymentService.process(
                "order-1", "cust-1", new BigDecimal("1500.00"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.getGatewayReference()).isEqualTo("PAY-ABC123");

        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("payment-completed");
    }

    @Test
    @DisplayName("a declined charge marks the payment FAILED and publishes PaymentFailedEvent")
    void declinedChargeFailsPayment() {
        when(paymentRepository.findByOrderId("order-2")).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(gatewayClient.charge(eq("order-2"), any(BigDecimal.class)))
                .thenReturn(new PaymentGatewayClient.GatewayResult(false, null, "Limit exceeded"));

        PaymentResponse response = paymentService.process(
                "order-2", "cust-1", new BigDecimal("500000.00"));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).isEqualTo("Limit exceeded");

        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().topic()).isEqualTo("payment-failed");
    }

    @Test
    @DisplayName("a redelivered order event never charges the customer a second time")
    void processIsIdempotentPerOrder() {
        Payment existing = Payment.builder()
                .id("pay-1").orderId("order-3").customerId("cust-1")
                .amount(new BigDecimal("999.00")).status(PaymentStatus.COMPLETED)
                .gatewayReference("PAY-EXISTING").build();

        when(paymentRepository.findByOrderId("order-3")).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.process(
                "order-3", "cust-1", new BigDecimal("999.00"));

        assertThat(response.getId()).isEqualTo("pay-1");
        verify(gatewayClient, never()).charge(any(), any());
        verify(eventPublisher, never()).publishEvent(any(OutboundEvent.class));
    }
}
