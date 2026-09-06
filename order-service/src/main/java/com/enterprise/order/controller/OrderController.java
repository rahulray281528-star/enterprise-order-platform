package com.enterprise.order.controller;

import com.enterprise.common.constant.OrderStatus;
import com.enterprise.common.dto.ApiResponse;
import com.enterprise.common.dto.PageResponse;
import com.enterprise.common.exception.ForbiddenException;
import com.enterprise.common.util.SecurityUtils;
import com.enterprise.order.dto.CreateOrderRequest;
import com.enterprise.order.dto.OrderResponse;
import com.enterprise.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Order creation, retrieval and cancellation")
public class OrderController {

    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order. Reserves inventory synchronously, then starts payment.")
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orderService.create(SecurityUtils.currentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order, "Order placed"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get one order. Customers may only read their own.")
    public ResponseEntity<ApiResponse<OrderResponse>> get(@PathVariable String orderId) {
        OrderResponse order = orderService.getById(
                orderId, SecurityUtils.currentUserId(), SecurityUtils.isAdmin());
        return ResponseEntity.ok(ApiResponse.success(order, "Order retrieved"));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List a customer's orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> byCustomer(
            @PathVariable String customerId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!SecurityUtils.isAdmin() && !SecurityUtils.currentUserId().equals(customerId)) {
            throw new ForbiddenException("You may only list your own orders");
        }

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return ResponseEntity.ok(ApiResponse.success(
                orderService.findByCustomer(customerId, status, pageable), "Orders retrieved"));
    }

    @PutMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order that has not yet been paid for")
    public ResponseEntity<ApiResponse<OrderResponse>> cancel(@PathVariable String orderId) {
        OrderResponse order = orderService.cancel(
                orderId, SecurityUtils.currentUserId(), SecurityUtils.isAdmin());
        return ResponseEntity.ok(ApiResponse.success(order, "Order cancelled"));
    }
}
