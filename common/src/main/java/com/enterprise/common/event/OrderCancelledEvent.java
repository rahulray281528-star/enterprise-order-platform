package com.enterprise.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Published by order-service when an order is cancelled or compensated. */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class OrderCancelledEvent extends BaseEvent {

    private String orderId;

    private String customerId;

    private String reason;
}
