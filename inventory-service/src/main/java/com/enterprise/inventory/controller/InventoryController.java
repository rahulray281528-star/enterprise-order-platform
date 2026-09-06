package com.enterprise.inventory.controller;

import com.enterprise.common.dto.ApiResponse;
import com.enterprise.inventory.dto.InventoryResponse;
import com.enterprise.inventory.dto.ReservationRequest;
import com.enterprise.inventory.dto.ReservationResponse;
import com.enterprise.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
@Tag(name = "Inventory", description = "Stock levels, reservations and releases")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Current stock for a product")
    public ResponseEntity<ApiResponse<InventoryResponse>> get(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getByProductId(productId), "Inventory retrieved"));
    }

    @PostMapping("/reserve")
    @Operation(summary = "Reserve stock for an order (all-or-nothing, idempotent per order)")
    public ResponseEntity<ApiResponse<ReservationResponse>> reserve(
            @Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.reserve(request), "Inventory reserved"));
    }

    @PostMapping("/release")
    @Operation(summary = "Release a reservation (compensating action, idempotent)")
    public ResponseEntity<ApiResponse<ReservationResponse>> release(@RequestParam String orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.release(orderId), "Inventory released"));
    }

    @PostMapping("/restock")
    @Operation(summary = "Add stock for a product (ADMIN only)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @RequestParam String productId,
            @RequestParam @Positive int quantity) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.restock(productId, quantity), "Stock added"));
    }
}
