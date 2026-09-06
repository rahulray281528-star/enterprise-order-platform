package com.enterprise.common.event;

import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Published by order-service after a successful payment. */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class OrderConfirmedEvent extends BaseEvent {

    private String orderId;

    private String customerId;

    private String paymentId;

    private BigDecimal totalAmount;
}
