package com.enterprise.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real payment provider (Razorpay, Stripe, a bank gateway).
 *
 * <p>It is deliberately a separate component behind its own interface-shaped boundary:
 * swapping this for a real HTTP client changes this class only. The decline rule is
 * deterministic rather than random so tests and the README walkthrough are repeatable.</p>
 */
@Component
@Slf4j
public class PaymentGatewayClient {

    private final PaymentGatewayProperties properties;

    public PaymentGatewayClient(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    public GatewayResult charge(String orderId, BigDecimal amount) {
        simulateLatency();

        if (amount.compareTo(properties.getDeclineAtOrAbove()) >= 0) {
            log.info("Gateway declined order {} for amount {} (limit {})",
                    orderId, amount, properties.getDeclineAtOrAbove());
            return new GatewayResult(false, null,
                    "Transaction declined: amount exceeds the per-order limit of "
                            + properties.getDeclineAtOrAbove());
        }

        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("Gateway approved order {} for amount {} with reference {}", orderId, amount, reference);
        return new GatewayResult(true, reference, null);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(properties.getLatencyMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Outcome returned by the gateway. */
    public record GatewayResult(boolean approved, String reference, String failureReason) {
    }
}
