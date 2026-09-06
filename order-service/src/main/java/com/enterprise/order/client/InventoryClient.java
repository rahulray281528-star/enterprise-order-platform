package com.enterprise.order.client;

import com.enterprise.order.dto.InventoryReserveRequest;
import com.enterprise.order.dto.InventoryReserveResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Synchronous call to inventory-service, resolved through Eureka by service name.
 *
 * <p>Reservation is synchronous on purpose: the customer needs an immediate yes or no
 * before the order is accepted. Everything after that point is asynchronous.</p>
 */
@FeignClient(name = "inventory-service", path = "/api/v1/inventory")
public interface InventoryClient {

    @PostMapping("/reserve")
    InventoryReserveResult reserve(@RequestBody InventoryReserveRequest request);

    @PostMapping("/release")
    InventoryReserveResult release(@RequestParam("orderId") String orderId);
}
