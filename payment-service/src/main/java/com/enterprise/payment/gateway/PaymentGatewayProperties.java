package com.enterprise.payment.gateway;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment.gateway")
public class PaymentGatewayProperties {

    /**
     * Orders at or above this total are declined by the simulated gateway. This gives a
     * deterministic way to exercise the failure and compensation path without wiring a
     * real payment provider - see the README walkthrough.
     */
    private BigDecimal declineAtOrAbove = new BigDecimal("200000.00");

    /** Simulated network latency of the upstream provider, in milliseconds. */
    private long latencyMillis = 250L;
}
