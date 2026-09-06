package com.enterprise.common.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Published by inventory-service when stock could not be held. */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class InventoryReservationFailedEvent extends BaseEvent {

    private String orderId;

    private String productId;

    private String reason;
}
