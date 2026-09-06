package com.enterprise.payment.controller;

import com.enterprise.common.dto.ApiResponse;
import com.enterprise.payment.dto.PaymentRequest;
import com.enterprise.payment.dto.PaymentResponse;
import com.enterprise.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment records and manual initiation")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Manually initiate a payment (ADMIN only; the normal path is event-driven)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse payment = paymentService.process(
                request.getOrderId(), request.getCustomerId(), request.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(payment, "Payment processed"));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get a payment by id")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(@PathVariable String paymentId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getById(paymentId), "Payment retrieved"));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get the payment for an order")
    public ResponseEntity<ApiResponse<PaymentResponse>> getByOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getByOrderId(orderId), "Payment retrieved"));
    }
}
