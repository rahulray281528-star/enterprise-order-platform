package com.enterprise.common.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Published by order-service once an order row exists and inventory is reserved. */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class OrderCreatedEvent extends BaseEvent {

    private String orderId;

    private String customerId;

    private BigDecimal totalAmount;

    private List<OrderItemPayload> items;
}
