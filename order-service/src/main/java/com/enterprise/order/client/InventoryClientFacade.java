package com.enterprise.order.client;

import com.enterprise.common.exception.ServiceUnavailableException;
import com.enterprise.order.dto.InventoryReserveRequest;
import com.enterprise.order.dto.InventoryReserveResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilience wrapper around {@link InventoryClient}.
 *
 * <p>Why this exists: inventory-service is on the critical path of order creation. A
 * slow or dead instance would otherwise tie up order-service threads until they are
 * exhausted, taking down an unrelated part of the system. The retry absorbs a transient
 * blip, and the circuit breaker stops hammering a dependency that is genuinely down,
 * failing fast with a clear 503 instead.</p>
 *
 * <p>The fallback deliberately does NOT pretend the reservation succeeded. Returning a
 * fake success here would let an order proceed to payment for stock that was never
 * held.</p>
 */
@Component
@Slf4j
public class InventoryClientFacade {

    private final InventoryClient inventoryClient;

    public InventoryClientFacade(InventoryClient inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "reserveFallback")
    @Retry(name = "inventoryService", fallbackMethod = "reserveFallback")
    public InventoryReserveResult reserve(InventoryReserveRequest request) {
        return inventoryClient.reserve(request);
    }

    @SuppressWarnings("unused")
    private InventoryReserveResult reserveFallback(InventoryReserveRequest request, Throwable throwable) {
        log.error("Inventory reservation unavailable for order {}: {}",
                request.getOrderId(), throwable.toString());
        throw new ServiceUnavailableException(
                "Inventory service is unavailable, please retry shortly", throwable);
    }

    @CircuitBreaker(name = "inventoryService", fallbackMethod = "releaseFallback")
    public InventoryReserveResult release(String orderId) {
        return inventoryClient.release(orderId);
    }

    @SuppressWarnings("unused")
    private InventoryReserveResult releaseFallback(String orderId, Throwable throwable) {
        // Release is also driven by an event consumed directly by inventory-service,
        // so a failure here is logged rather than propagated.
        log.error("Inventory release call failed for order {}: {}", orderId, throwable.toString());
        return null;
    }
}
